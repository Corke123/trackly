#!/usr/bin/env bash
set -euo pipefail

: "${ENVIRONMENT:?ENVIRONMENT must be set to staging or production}"
: "${RG:?RG must be set to the resource group}"

{
  echo "## Live traffic — ${ENVIRONMENT}"
  echo
  echo "| App | Revision | Weight |"
  echo "|---|---|---|"
  for service in identity board notification gateway; do
    app="${service}-${ENVIRONMENT}"
    az containerapp show -n "$app" -g "$RG" \
      --query "properties.configuration.ingress.traffic[].{r:revisionName,w:weight}" -o tsv 2>/dev/null |
      while IFS=$'\t' read -r rev weight; do
        echo "| $app | \`${rev:-latest}\` | ${weight}% |"
      done
  done
  if [[ "$ENVIRONMENT" == "production" ]]; then
    echo
    echo "Roll back with the **Rollback** workflow; the previous revision is still warm."
  fi
} >> "$GITHUB_STEP_SUMMARY"
