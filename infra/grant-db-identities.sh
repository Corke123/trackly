#!/usr/bin/env bash

set -euo pipefail

ENV_NAME="${1:?Usage: grant-db-identities.sh <staging|production>}"
INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PGHOST=$(terraform -chdir="${INFRA_DIR}/environments/shared" output -raw postgres_fqdn)
PG_SERVER=$(terraform -chdir="${INFRA_DIR}/environments/shared" output -raw postgres_server_name)
PG_RG=$(terraform -chdir="${INFRA_DIR}/environments/shared" output -raw resource_group_name)

ADMIN_UPN=$(az postgres flexible-server microsoft-entra-admin list \
  -g "$PG_RG" -s "$PG_SERVER" --query "[0].principalName" -o tsv 2>/dev/null || true)
ADMIN_UPN="${ADMIN_UPN:-$(az ad signed-in-user show --query userPrincipalName -o tsv)}"

IDENTITIES_JSON=$(terraform -chdir="${INFRA_DIR}/environments/${ENV_NAME}" output -json app_identity_names)
DATABASES_JSON=$(terraform -chdir="${INFRA_DIR}/environments/${ENV_NAME}" output -json database_names)

export PGPASSWORD
PGPASSWORD=$(az account get-access-token --resource-type oss-rdbms --query accessToken -o tsv)

echo "==> Creating server-level principals on ${PGHOST} as ${ADMIN_UPN}"
for service in board identity notification; do
  principal=$(jq -r ".${service}" <<<"$IDENTITIES_JSON")
  echo "    ${principal}"
  psql "host=${PGHOST} port=5432 dbname=postgres user=${ADMIN_UPN} sslmode=require" \
    -v ON_ERROR_STOP=1 -q -c \
    "SELECT * FROM pgaadauth_create_principal('${principal}', false, false);" ||
    echo "      already exists"
done

echo "==> Transferring database ownership"
for service in board identity notification; do
  principal=$(jq -r ".${service}" <<<"$IDENTITIES_JSON")
  database=$(jq -r ".${service}" <<<"$DATABASES_JSON")
  echo "    ${database} -> ${principal}"
  psql "host=${PGHOST} port=5432 dbname=postgres user=${ADMIN_UPN} sslmode=require" \
    -v ON_ERROR_STOP=1 -q -c \
    "ALTER DATABASE \"${database}\" OWNER TO \"${principal}\";"
  psql "host=${PGHOST} port=5432 dbname=${database} user=${ADMIN_UPN} sslmode=require" \
    -v ON_ERROR_STOP=1 -q -c \
    "ALTER SCHEMA public OWNER TO \"${principal}\";"
done

echo
echo "Done. ${ENV_NAME} apps can now authenticate to PostgreSQL with their managed identities."
