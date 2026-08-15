#!/usr/bin/env bash
set -euo pipefail

: "${ENVIRONMENT:?ENVIRONMENT must be set to staging or production}"
: "${SERVICE:?SERVICE must be set to all or a single service}"
: "${RG:?RG must be set to the resource group}"

if [[ "$SERVICE" == "all" ]]; then
  services="gateway identity board notification"
else
  services="$SERVICE"
fi

{
  echo "## Rollback — ${ENVIRONMENT}"
  echo
  echo "| App | From | To |"
  echo "|---|---|---|"
} >> "$GITHUB_STEP_SUMMARY"

failed=0
for service in $services; do
  app="${service}-${ENVIRONMENT}"

  current=$(az containerapp show -n "$app" -g "$RG" \
    --query "properties.configuration.ingress.traffic | sort_by(@, &weight) | [-1].revisionName" -o tsv)

  target=$(az containerapp revision list -n "$app" -g "$RG" \
    --query "sort_by([?properties.active], &properties.createdTime) | reverse(@) | [?name!='${current}'] | [0].name" -o tsv)

  if [[ -z "$target" || "$target" == "null" ]]; then
    echo "::error::No previous active revision for ${app} to roll back to" >&2
    echo "| $app | \`$current\` | none available |" >> "$GITHUB_STEP_SUMMARY"
    failed=1
    continue
  fi

  az containerapp ingress traffic set -n "$app" -g "$RG" \
    --revision-weight "${target}=100" "${current}=0" --output none

  echo "${app}: ${current} -> ${target}"
  echo "| $app | \`$current\` | \`$target\` |" >> "$GITHUB_STEP_SUMMARY"
done

{
  echo
  echo "Schema migrations are **not** rolled back; only traffic moved."
} >> "$GITHUB_STEP_SUMMARY"

exit "$failed"
