#!/usr/bin/env bash
set -euo pipefail

: "${STACK:?STACK must be set to shared, staging or production}"
: "${MODE:?MODE must be set to plan or apply}"

cd "infra/environments/${STACK}"

terraform init -input=false \
  -backend-config="resource_group_name=${TFSTATE_RESOURCE_GROUP}" \
  -backend-config="storage_account_name=${TFSTATE_STORAGE_ACCOUNT}" \
  -backend-config="container_name=${TFSTATE_CONTAINER}"

args=(
  -input=false
  -lock-timeout=5m
  -var "subscription_id=${AZURE_SUBSCRIPTION_ID}"
)

if [[ "$STACK" == shared ]]; then
  args+=(
    -var "operator_object_id=${OPERATOR_OBJECT_ID}"
    -var "operator_ip=${OPERATOR_IP}"
    -var "github_infra_identity_principal_id=${INFRA_IDENTITY_PRINCIPAL_ID}"
    -var "budget_alert_email=${BUDGET_ALERT_EMAIL}"
  )
else
  suffix=${STACK^^}
  secret="CLIENT_SECRET_${suffix}"
  hash="CLIENT_SECRET_HASH_${suffix}"
  export TF_VAR_client_secret="${!secret}"
  export TF_VAR_client_secret_bcrypt="${!hash}"
  args+=(
    -var "tfstate_resource_group=${TFSTATE_RESOURCE_GROUP}"
    -var "tfstate_storage_account=${TFSTATE_STORAGE_ACCOUNT}"
  )
fi

case "$MODE" in
  plan)
    terraform validate
    terraform plan -no-color "${args[@]}" | tee plan.txt
    ;;
  apply)
    terraform apply -auto-approve "${args[@]}"
    ;;
  *)
    echo "MODE must be plan or apply, got ${MODE}" >&2
    exit 1
    ;;
esac
