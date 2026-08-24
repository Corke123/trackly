#!/usr/bin/env bash
set -euo pipefail

# The blue-green action verifies each revision on its own FQDN before that revision takes traffic
# (ADR 0007). This verifies the environment the way a user reaches it — through the public ingress,
# after the traffic shift — which is the only check that can prove the shift itself took effect.
# staging then goes on to the acceptance suite; production has nothing behind it, so this is the
# check whose failure triggers the automatic rollback.

: "${FQDN:?FQDN must be set to the public hostname of the gateway}"
: "${ENVIRONMENT:?ENVIRONMENT must be set to staging or production}"
: "${EXPECTED_SHA:=}"
: "${TIMEOUT:=300}"

url="https://${FQDN}"
echo "Verifying ${url}${EXPECTED_SHA:+ for ${EXPECTED_SHA}}"

deadline=$(( $(date +%s) + TIMEOUT ))
until curl -fsS --max-time 30 "${url}/actuator/health" >/dev/null 2>&1; do
  if [ "$(date +%s)" -ge "$deadline" ]; then
    echo "::error::${ENVIRONMENT} did not answer /actuator/health within ${TIMEOUT}s after the traffic shift"
    exit 1
  fi
  sleep 10
done

serving=$(curl -fsS --max-time 30 "${url}/actuator/info" | jq -r '.build.revision // empty')
if [ -n "$EXPECTED_SHA" ] && [ "$serving" != "$EXPECTED_SHA" ]; then
  echo "::error::${ENVIRONMENT} serves build.revision=${serving:-<none>}, expected ${EXPECTED_SHA}"
  exit 1
fi

# The SPA is a layer of the gateway image (ADR 0006), so a gateway that answers the actuator but not
# its own document root is a broken release that the actuator alone would call healthy.
if ! curl -fsS --max-time 30 -o /dev/null "${url}/"; then
  echo "::error::${ENVIRONMENT} serves the actuator but not the single-page app"
  exit 1
fi

{
  echo "### Post-release verification — ${ENVIRONMENT}"
  echo
  echo "| | |"
  echo "|---|---|"
  echo "| URL | ${url} |"
  echo "| Serving | \`${serving:-<none>}\` |"
  if [ -n "$EXPECTED_SHA" ]; then
    echo "| Revision | asserted equal to \`${EXPECTED_SHA}\` |"
    echo "| Checks | \`/actuator/health\`, \`/actuator/info\` revision, \`/\` |"
  else
    echo "| Revision | not asserted — the gateway is not part of this release |"
    echo "| Checks | \`/actuator/health\`, \`/\` |"
  fi
  echo
} >> "$GITHUB_STEP_SUMMARY"

echo "Verified: ${ENVIRONMENT} serves ${serving} through its public ingress"
