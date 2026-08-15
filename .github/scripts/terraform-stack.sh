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
  -var "SUBSCRIPTION_ID=${AZURE_SUBSCRIPTION_ID}"
)

if [[ "$STACK" == shared ]]; then
  args+=(
    -var "OPERATOR_OBJECT_ID=${OPERATOR_OBJECT_ID}"
    -var "OPERATOR_IP=${OPERATOR_IP}"
    -var "GITHUB_INFRA_IDENTITY_PRINCIPAL_ID=${INFRA_IDENTITY_PRINCIPAL_ID}"
    -var "BUDGET_ALERT_EMAIL=${BUDGET_ALERT_EMAIL}"
  )
else
  suffix=${STACK^^}
  secret="CLIENT_SECRET_${suffix}"
  hash="CLIENT_SECRET_HASH_${suffix}"
  export TF_VAR_CLIENT_SECRET="${!secret}"
  export TF_VAR_CLIENT_SECRET_BCRYPT="${!hash}"
  args+=(
    -var "TFSTATE_RESOURCE_GROUP=${TFSTATE_RESOURCE_GROUP}"
    -var "TFSTATE_STORAGE_ACCOUNT=${TFSTATE_STORAGE_ACCOUNT}"
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
