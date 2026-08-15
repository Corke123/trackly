#!/usr/bin/env bash
set -euo pipefail

: "${ACTION:?ACTION must be set to stop or start}"

server=$(az postgres flexible-server list -g rg-trackly-shared --query "[0].name" -o tsv)
state=$(az postgres flexible-server show -n "$server" -g rg-trackly-shared --query state -o tsv)
echo "${server} is ${state}, requested ${ACTION}"

case "$ACTION:$state" in
  stop:Ready)
    az postgres flexible-server stop -n "$server" -g rg-trackly-shared --output none
    result="stopped" ;;
  start:Stopped|start:Disabled)
    az postgres flexible-server start -n "$server" -g rg-trackly-shared --output none
    result="started" ;;
  *)
    result="no change (already ${state})" ;;
esac

{
  echo "## Hibernate"
  echo
  echo "| | |"
  echo "|---|---|"
  echo "| Server | \`${server}\` |"
  echo "| Requested | ${ACTION} |"
  echo "| Result | ${result} |"
  echo
  echo "Stopped means compute is not billed; storage still is. A deploy wakes it automatically."
} >> "$GITHUB_STEP_SUMMARY"
