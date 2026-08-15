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

t0=$(date +%s)
now() { date +%s; }
since() { echo "$(($(now) - $1))"; }
say() { printf '[+%4ds] %s\n' "$(since "$t0")" "$*"; }

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

  local started phase
  started=$(now)
  printf '%s' "$(since "$t0")" >"${work}/${service}.offset"

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
  say "Currently serving: ${previous:-<none>}"
  printf '%s' "$(since "$started")" >"${work}/${service}.lookup"

  phase=$(now)
  az containerapp update -n "$app" -g "$RG" \
    --image "$image" --revision-suffix "$SUFFIX" --output none
  printf '%s' "$(since "$phase")" >"${work}/${service}.update"
  say "Created ${revision} at 0% traffic"

  phase=$(now)
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
        say "  $state"
        sleep 10
        ;;
    esac
  done
  printf '%s' "$(since "$phase")" >"${work}/${service}.provision"
  if [[ "$state" != "Provisioned" ]]; then
    echo "::error::Revision ${revision} did not provision within 10 minutes" >&2
    return 1
  fi
  say "Provisioned"

  [[ "$external" == *" ${service} "* ]] || return 0

  phase=$(now)
  local domain url deadline revision_sha
  domain=$(az containerapp env show \
    --ids "$(az containerapp show -n "$app" -g "$RG" --query properties.environmentId -o tsv)" \
    --query properties.defaultDomain -o tsv)
  url="https://${revision}.${domain}"
  say "Probing ${url}"

  deadline=$(($(now) + HEALTH_TIMEOUT))
  until curl -fsS --max-time 30 "${url}/actuator/health" >/dev/null 2>&1; do
    if [[ "$(now)" -ge "$deadline" ]]; then
      echo "::error::${revision} did not become healthy within ${HEALTH_TIMEOUT}s" >&2
      return 1
    fi
    sleep 10
  done
  say "Healthy after $(since "$phase")s"

  revision_sha=$(curl -fsS --max-time 30 "${url}/actuator/info" | jq -r '.build.revision // empty')
  if [[ "$revision_sha" != "$EXPECTED_SHA" ]]; then
    echo "::error::${revision} reports build.revision=${revision_sha:-<none>}, expected ${EXPECTED_SHA}" >&2
    return 1
  fi
  printf '%s' "$(since "$phase")" >"${work}/${service}.verify"
  say "Verified build.revision=${revision_sha}"
}

shift_traffic() {
  local service=$1
  local app="${service}-${ENVIRONMENT}"
  local revision="${app}--${SUFFIX}"
  local previous phase
  previous=$(cat "${work}/${service}.previous")

  phase=$(now)
  if [[ -n "$previous" && "$previous" != "$revision" ]]; then
    az containerapp ingress traffic set -n "$app" -g "$RG" \
      --revision-weight "${revision}=100" "${previous}=0" --output none
  else
    az containerapp ingress traffic set -n "$app" -g "$RG" \
      --revision-weight "${revision}=100" --output none
  fi
  printf '%s' "$(since "$phase")" >"${work}/${service}.shift"
  say "100% of traffic now on ${revision} (shift took $(since "$phase")s)"
}

timing() {
  local value
  value=$(cat "${work}/${1}.${2}" 2>/dev/null || true)
  printf '%s' "${value:---}"
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
    echo
    echo "### Where the time went"
    echo
    echo "All values in seconds. \`Forked at\` is the offset from the start of the step, so equal values"
    echo "mean the workers really did start together; \`update\` and \`provision\` are the Azure control"
    echo "plane, \`verify\` is the revision's own cold start plus health probe."
    echo
    echo "| App | Forked at | Lookup | Update | Provision | Verify | Shift |"
    echo "|---|---|---|---|---|---|---|"
    for service in $selected; do
      echo "| ${service}-${ENVIRONMENT} | $(timing "$service" offset) | $(timing "$service" lookup) |" \
        "$(timing "$service" update) | $(timing "$service" provision) | $(timing "$service" verify) |" \
        "$(timing "$service" shift) |"
    done
    echo
    echo "| Phase | Wall clock |"
    echo "|---|---|"
    echo "| Release, all services concurrent | ${release_seconds:-—}s |"
    echo "| Traffic shifts, sequential | ${shift_seconds:-—}s |"
  } >>"$GITHUB_STEP_SUMMARY"
}

finished_at() {
  local last
  last=$(sed -n 's/^\[+ *\([0-9]*\)s\].*/\1/p' "${work}/${1}.log" 2>/dev/null | tail -1)
  printf '%s' "${last:---}"
}

say "Releasing in parallel:${selected% }"

release_started=$(now)
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
release_seconds=$(since "$release_started")

for service in $selected; do
  echo "::group::${service}-service — $(cat "${work}/${service}.outcome"), ran +$(timing "$service" offset)s to +$(finished_at "$service")s"
  cat "${work}/${service}.log"
  echo "::endgroup::"
done
say "Release phase took ${release_seconds}s for ${#pids[@]} services"

if [[ -n "$failed" ]]; then
  echo "::error::Release failed for:${failed% }. No traffic was shifted." >&2
  summarise
  exit 1
fi

shift_started=$(now)
for service in $order; do
  if [[ " $selected " == *" ${service} "* ]]; then
    shift_traffic "$service"
  fi
done
shift_seconds=$(since "$shift_started")
say "Traffic shifts took ${shift_seconds}s"

summarise
