#!/usr/bin/env bash
set -euo pipefail

: "${SERVICES:?SERVICES must be set to the JSON array of services to deploy}"
: "${ENVIRONMENT:?ENVIRONMENT must be set to staging or production}"
: "${RG:?RG must be set to the resource group}"
: "${REGISTRY:?REGISTRY must be set to the ACR login server}"
: "${SUFFIX:?SUFFIX must be set to the revision suffix}"
: "${EXPECTED_SHA:?EXPECTED_SHA must be set to the commit under release}"
: "${HEALTH_TIMEOUT:=300}"

order="identity board notification gateway"
external=" identity gateway "

azure_config_source="${AZURE_CONFIG_DIR:-${HOME}/.azure}"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

selected=""
for service in $order; do
  if printf '%s' "$SERVICES" | jq -e --arg s "${service}-service" 'index($s) != null' >/dev/null; then
    selected="${selected}${service} "
  fi
done

if [[ -z "$selected" ]]; then
  echo "Nothing to deploy"
  exit 0
fi

release() {
  local service=$1
  local app="${service}-${ENVIRONMENT}"
  local revision="${app}--${SUFFIX}"
  local image="${REGISTRY}/trackly/${service}-service:sha-${EXPECTED_SHA}"

  export AZURE_CONFIG_DIR="${work}/azure/${service}"
  mkdir -p "$AZURE_CONFIG_DIR"
  cp -R "${azure_config_source}/." "$AZURE_CONFIG_DIR/"

  local previous fqdn
  previous=$(az containerapp show -n "$app" -g "$RG" \
    --query "properties.configuration.ingress.traffic | sort_by(@, &weight) | [-1].revisionName" -o tsv 2>/dev/null || true)
  fqdn=$(az containerapp show -n "$app" -g "$RG" \
    --query "properties.configuration.ingress.fqdn" -o tsv 2>/dev/null || true)
  printf '%s' "$previous" >"${work}/${service}.previous"
  printf '%s' "$fqdn" >"${work}/${service}.fqdn"
  echo "Currently serving: ${previous:-<none>}"

  az containerapp update -n "$app" -g "$RG" \
    --image "$image" --revision-suffix "$SUFFIX" --output none
  echo "Created ${revision} at 0% traffic"

  local state=""
  for _ in $(seq 1 60); do
    state=$(az containerapp revision show -n "$app" -g "$RG" --revision "$revision" \
      --query "properties.provisioningState" -o tsv 2>/dev/null || echo Unknown)
    case "$state" in
      Provisioned) break ;;
      Failed | Degraded)
        echo "::error::Revision ${revision} provisioning ${state}" >&2
        return 1
        ;;
      *)
        echo "  $state"
        sleep 10
        ;;
    esac
  done
  if [[ "$state" != "Provisioned" ]]; then
    echo "::error::Revision ${revision} did not provision within 10 minutes" >&2
    return 1
  fi
  echo "Provisioned"

  [[ "$external" == *" ${service} "* ]] || return 0

  local domain url deadline revision_sha
  domain=$(az containerapp env show \
    --ids "$(az containerapp show -n "$app" -g "$RG" --query properties.environmentId -o tsv)" \
    --query properties.defaultDomain -o tsv)
  url="https://${revision}.${domain}"
  echo "Probing ${url}"

  deadline=$(($(date +%s) + HEALTH_TIMEOUT))
  until curl -fsS --max-time 30 "${url}/actuator/health" >/dev/null 2>&1; do
    if [[ "$(date +%s)" -ge "$deadline" ]]; then
      echo "::error::${revision} did not become healthy within ${HEALTH_TIMEOUT}s" >&2
      return 1
    fi
    sleep 10
  done
  echo "Healthy"

  revision_sha=$(curl -fsS --max-time 30 "${url}/actuator/info" | jq -r '.build.revision // empty')
  if [[ "$revision_sha" != "$EXPECTED_SHA" ]]; then
    echo "::error::${revision} reports build.revision=${revision_sha:-<none>}, expected ${EXPECTED_SHA}" >&2
    return 1
  fi
  echo "Verified build.revision=${revision_sha}"
}

shift_traffic() {
  local service=$1
  local app="${service}-${ENVIRONMENT}"
  local revision="${app}--${SUFFIX}"
  local previous
  previous=$(cat "${work}/${service}.previous")

  if [[ -n "$previous" && "$previous" != "$revision" ]]; then
    az containerapp ingress traffic set -n "$app" -g "$RG" \
      --revision-weight "${revision}=100" "${previous}=0" --output none
  else
    az containerapp ingress traffic set -n "$app" -g "$RG" \
      --revision-weight "${revision}=100" --output none
  fi
  echo "100% of traffic now on ${revision}"
}

summarise() {
  local service app previous fqdn url outcome
  {
    echo "## Release — ${ENVIRONMENT}"
    echo
    echo "| App | Previous revision | New revision | URL | Outcome |"
    echo "|---|---|---|---|---|"
    for service in $selected; do
      app="${service}-${ENVIRONMENT}"
      previous=$(cat "${work}/${service}.previous" 2>/dev/null || true)
      fqdn=$(cat "${work}/${service}.fqdn" 2>/dev/null || true)
      outcome=$(cat "${work}/${service}.outcome")
      url="—"
      [[ -n "$fqdn" ]] && url="https://${fqdn}"
      echo "| ${app} | \`${previous:-none}\` | \`${app}--${SUFFIX}\` | ${url} | ${outcome} |"
    done
    echo
    echo "Image tag \`sha-${EXPECTED_SHA}\`, revision suffix \`${SUFFIX}\`."
  } >>"$GITHUB_STEP_SUMMARY"
}

echo "Releasing in parallel:${selected% }"

pids=()
names=()
for service in $selected; do
  release "$service" >"${work}/${service}.log" 2>&1 &
  pids+=("$!")
  names+=("$service")
done

failed=""
for i in "${!pids[@]}"; do
  service="${names[$i]}"
  if wait "${pids[$i]}"; then
    printf 'released' >"${work}/${service}.outcome"
  else
    printf 'failed' >"${work}/${service}.outcome"
    failed="${failed}${service} "
  fi
done

for service in $selected; do
  echo "::group::${service}-service — $(cat "${work}/${service}.outcome")"
  cat "${work}/${service}.log"
  echo "::endgroup::"
done

if [[ -n "$failed" ]]; then
  echo "::error::Release failed for:${failed% }. No traffic was shifted." >&2
  summarise
  exit 1
fi

for service in $order; do
  if [[ " $selected " == *" ${service} "* ]]; then
    shift_traffic "$service"
  fi
done

summarise
