#!/usr/bin/env bash

set -euo pipefail

: "${SUBSCRIPTION_ID:?Set SUBSCRIPTION_ID}"
: "${GITHUB_OWNER:?Set GITHUB_OWNER (the GitHub user or organisation owning the repository)}"
GITHUB_REPO="${GITHUB_REPO:-trackly}"
LOCATION="${LOCATION:-westeurope}"

RG="rg-trackly-bootstrap"
CONTAINER="tfstate"
ISSUER="https://token.actions.githubusercontent.com"
AUDIENCE="api://AzureADTokenExchange"
SUB_SCOPE="/subscriptions/${SUBSCRIPTION_ID}"

az account set --subscription "$SUBSCRIPTION_ID"
TENANT_ID=$(az account show --query tenantId -o tsv)

echo "==> Registering resource providers"
for ns in Microsoft.App Microsoft.OperationalInsights Microsoft.ContainerRegistry \
  Microsoft.DBforPostgreSQL Microsoft.ServiceBus Microsoft.KeyVault \
  Microsoft.ManagedIdentity Microsoft.Storage Microsoft.Consumption; do
  az provider register --namespace "$ns" --wait
done

echo "==> Bootstrap resource group ${RG}"
az group create -n "$RG" -l "$LOCATION" --tags project=trackly managed_by=bootstrap -o none

echo "==> Remote-state storage account"
SA=$(az storage account list -g "$RG" --query "[?starts_with(name,'sttracklytf')].name | [0]" -o tsv)
if [[ -z "$SA" || "$SA" == "null" ]]; then
  SA="sttracklytf$(openssl rand -hex 3)"
  az storage account create -n "$SA" -g "$RG" -l "$LOCATION" \
    --sku Standard_LRS --kind StorageV2 --min-tls-version TLS1_2 \
    --https-only true --allow-blob-public-access false \
    --allow-shared-key-access false -o none
  az storage account blob-service-properties update -n "$SA" -g "$RG" \
    --enable-versioning true --enable-delete-retention true --delete-retention-days 30 -o none
fi
echo "    ${SA}"

SA_ID=$(az storage account show -n "$SA" -g "$RG" --query id -o tsv)

ME=$(az ad signed-in-user show --query id -o tsv)
if ! az role assignment list --assignee-object-id "$ME" --scope "$SA_ID" \
  --query "[?roleDefinitionName=='Storage Blob Data Owner'] | [0]" -o tsv | grep -q .; then
  az role assignment create --assignee-object-id "$ME" --assignee-principal-type User \
    --role "Storage Blob Data Owner" --scope "$SA_ID" -o none
  echo "    waiting 60s for RBAC to propagate before the data-plane call"
  sleep 60
fi
az storage container create --account-name "$SA" -n "$CONTAINER" --auth-mode login -o none

echo "==> GitHub federated identities"
for name in infra staging production; do
  az identity create -g "$RG" -n "id-trackly-github-${name}" -l "$LOCATION" -o none
done

INFRA_CLIENT_ID=$(az identity show -g "$RG" -n id-trackly-github-infra --query clientId -o tsv)
INFRA_PRINCIPAL=$(az identity show -g "$RG" -n id-trackly-github-infra --query principalId -o tsv)
STAGING_CLIENT_ID=$(az identity show -g "$RG" -n id-trackly-github-staging --query clientId -o tsv)
PROD_CLIENT_ID=$(az identity show -g "$RG" -n id-trackly-github-production --query clientId -o tsv)

echo "==> Federated credentials"
add_fic() {
  local identity="$1" name="$2" subject="$3"
  if az identity federated-credential show --identity-name "$identity" -g "$RG" -n "$name" -o none 2>/dev/null; then
    echo "    ${identity}/${name} exists"
  else
    az identity federated-credential create --identity-name "$identity" -g "$RG" -n "$name" \
      --issuer "$ISSUER" --subject "$subject" --audiences "$AUDIENCE" -o none
    echo "    ${identity}/${name} -> ${subject}"
  fi
}

SUB_PREFIX=$(gh api "repos/${GITHUB_OWNER}/${GITHUB_REPO}/actions/oidc/customization/sub" \
  --jq '.sub_claim_prefix // empty' 2>/dev/null || true)
SUB_PREFIX="${SUB_PREFIX:-repo:${GITHUB_OWNER}/${GITHUB_REPO}}"
echo "    subject prefix: ${SUB_PREFIX}"

add_fic id-trackly-github-infra gh-pull-request "${SUB_PREFIX}:pull_request"
add_fic id-trackly-github-infra gh-main "${SUB_PREFIX}:ref:refs/heads/main"
add_fic id-trackly-github-staging gh-env-staging "${SUB_PREFIX}:environment:staging"
add_fic id-trackly-github-production gh-env-production "${SUB_PREFIX}:environment:production"

echo "==> Role assignments for the infra identity"
grant() {
  local role="$1" scope="$2"
  az role assignment create --assignee-object-id "$INFRA_PRINCIPAL" \
    --assignee-principal-type ServicePrincipal --role "$role" --scope "$scope" -o none 2>/dev/null ||
    echo "    ${role} already granted"
}

grant "Contributor" "$SUB_SCOPE"
grant "Role Based Access Control Administrator" "$SUB_SCOPE"
grant "Key Vault Certificates Officer" "$SUB_SCOPE"
grant "Key Vault Secrets Officer" "$SUB_SCOPE"
grant "Storage Blob Data Contributor" "${SA_ID}/blobServices/default/containers/${CONTAINER}"

echo "==> GitHub environments"
gh api -X PUT "repos/${GITHUB_OWNER}/${GITHUB_REPO}/environments/staging" --silent
gh api -X PUT "repos/${GITHUB_OWNER}/${GITHUB_REPO}/environments/production" --silent

cat <<EOF

======================================================================
Bootstrap complete. Four things left, all by hand.
======================================================================

1. Set the GitHub variables:

gh variable set AZURE_TENANT_ID         --body "${TENANT_ID}"
gh variable set AZURE_SUBSCRIPTION_ID   --body "${SUBSCRIPTION_ID}"
gh variable set AZURE_CLIENT_ID         --body "${INFRA_CLIENT_ID}"
gh variable set TFSTATE_RESOURCE_GROUP  --body "${RG}"
gh variable set TFSTATE_STORAGE_ACCOUNT --body "${SA}"
gh variable set TFSTATE_CONTAINER       --body "${CONTAINER}"

gh variable set AZURE_CLIENT_ID --env staging    --body "${STAGING_CLIENT_ID}"
gh variable set AZURE_CLIENT_ID --env production --body "${PROD_CLIENT_ID}"
gh variable set RESOURCE_GROUP  --env staging    --body "rg-trackly-staging"
gh variable set RESOURCE_GROUP  --env production --body "rg-trackly-production"

# Inputs the shared stack needs, which infra.yaml passes on your behalf. Kept as variables rather than
# committed tfvars so your object id and email are not in the repository.
gh variable set OPERATOR_OBJECT_ID          --body "${ME}"
gh variable set INFRA_IDENTITY_PRINCIPAL_ID --body "${INFRA_PRINCIPAL}"
gh variable set OPERATOR_IP                 --body "\$(curl -s ifconfig.me)"
gh variable set BUDGET_ALERT_EMAIL           --body "you@example.com"

# ACR variables, after the shared stack is applied:
gh variable set ACR_NAME         --body "\$(terraform -chdir=infra/environments/shared output -raw acr_name)"
gh variable set ACR_LOGIN_SERVER --body "\$(terraform -chdir=infra/environments/shared output -raw acr_login_server)"

2. Generate the OAuth2 client secret for each environment. identity-service
   stores a bcrypt hash and the gateway holds the plaintext, so both forms are
   needed. Repeat for production:

for env in STAGING PRODUCTION; do
  SECRET=\$(openssl rand -base64 30 | tr -d '/+=')
  HASH=\$(htpasswd -bnBC 10 "" "\$SECRET" | tr -d ':\\n')
  gh secret set "CLIENT_SECRET_\${env}"      --body "\$SECRET"
  gh secret set "CLIENT_SECRET_HASH_\${env}" --body "{bcrypt}\$HASH"
done

   These are repository secrets, not environment secrets: infra.yaml cannot
   bind a GitHub environment without changing its OIDC subject away from what
   the infra identity accepts.

3. In the GitHub UI, add a **required reviewers** protection rule to the
   'production' environment. That rule is the manual approval gate — without
   it, this pipeline performs continuous deployment, not continuous delivery.

4. Apply the shared stack, then follow infra/README.md:

terraform -chdir=infra/environments/shared init \\
  -backend-config="resource_group_name=${RG}" \\
  -backend-config="storage_account_name=${SA}" \\
  -backend-config="container_name=${CONTAINER}"

terraform -chdir=infra/environments/shared apply \\
  -var subscription_id="${SUBSCRIPTION_ID}" \\
  -var operator_object_id="${ME}" \\
  -var operator_ip="\$(curl -s ifconfig.me)" \\
  -var github_infra_identity_principal_id="${INFRA_PRINCIPAL}" \\
  -var budget_alert_email="you@example.com"

EOF
