#!/usr/bin/env bash
set -euo pipefail

: "${CHANGED:?CHANGED must be set to the paths-filter changes JSON}"
: "${SHARED:=false}"
: "${CLIENT:=false}"
: "${FORCE_ALL:=false}"

all='["board-service","notification-service","identity-service","gateway-service"]'

if [[ "$SHARED" == "true" || "$FORCE_ALL" == "true" ]]; then
  services="$all"
  client_build=true
  reason="shared paths changed"
else
  services=$(printf '%s' "$CHANGED" | jq -c 'map(select(. != "shared" and . != "trackly-client"))')
  client_build="$CLIENT"
  reason="path filter"
fi

backend_services=$(printf '%s' "$services" | jq -c 'map(select(. != "gateway-service"))')
gateway=$(printf '%s' "$services" | jq -r 'index("gateway-service") != null')

{
  echo "services=$services"
  echo "backend-services=$backend_services"
  echo "gateway=$gateway"
  echo "client=$client_build"
} >> "$GITHUB_OUTPUT"

{
  echo "## Change detection"
  echo
  echo "| | |"
  echo "|---|---|"
  echo "| Reason | $reason |"
  echo "| Services to build | \`$services\` |"
  echo "| Build the gateway | \`$gateway\` |"
  echo "| Build the client | \`$client_build\` |"
} >> "$GITHUB_STEP_SUMMARY"
