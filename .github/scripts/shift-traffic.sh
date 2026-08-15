#!/usr/bin/env bash
set -euo pipefail

: "${STACKS:?STACKS must be set to a space separated list of stacks}"

for stack in $STACKS; do
  [ "$stack" = "shared" ] && continue
  rg="rg-trackly-${stack}"
  for service in identity board notification gateway; do
    app="${service}-${stack}"
    latest=$(az containerapp show -n "$app" -g "$rg" --query properties.latestRevisionName -o tsv 2>/dev/null || true)
    [ -z "$latest" ] && continue
    az containerapp ingress traffic set -n "$app" -g "$rg" --revision-weight "${latest}=100" --output none
    echo "${app} -> ${latest}"
  done
done
