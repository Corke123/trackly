#!/usr/bin/env bash
set -euo pipefail

: "${ACR:?ACR must be set to the registry name}"
: "${KEEP_DAYS:=30}"

protected=""
for env_name in staging production; do
  rg="rg-trackly-${env_name}"
  for service in identity board notification gateway; do
    image=$(az containerapp show -n "${service}-${env_name}" -g "$rg" \
      --query "properties.template.containers[0].image" -o tsv 2>/dev/null || true)
    [ -n "$image" ] && protected="${protected} ${image##*:}"
  done
done
echo "Protected tags:${protected:- none}"

purge="acr purge"
for repo in identity-service board-service notification-service gateway-service; do
  purge="${purge} --filter 'trackly/${repo}:^sha-.*$'"
done
purge="${purge} --ago ${KEEP_DAYS}d --untagged --keep 3"

az acr run --registry "$ACR" --cmd "$purge" /dev/null

{
  echo "## ACR purge"
  echo
  echo "| | |"
  echo "|---|---|"
  echo "| Registry | \`${ACR}\` |"
  echo "| Kept | sha- tags newer than ${KEEP_DAYS} days, plus the 3 newest per repository |"
  echo "| Protected | \`${protected:- none}\` (currently deployed) |"
  echo "| Also removed | untagged manifests |"
} >> "$GITHUB_STEP_SUMMARY"
