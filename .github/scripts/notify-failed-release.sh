#!/usr/bin/env bash
set -euo pipefail

: "${RUN_URL:?RUN_URL must be set to the failing run}"
: "${WAKE:?WAKE must be set to the wake-database result}"
: "${STAGING:?STAGING must be set to the deploy-staging result}"
: "${ACCEPTANCE:?ACCEPTANCE must be set to the acceptance-staging result}"

short="${GITHUB_SHA:0:7}"
body=$(cat <<EOF
The release of \`${GITHUB_SHA}\` did not reach production.

| Stage | Result |
|---|---|
| Wake the database | ${WAKE} |
| Deploy staging | ${STAGING} |
| Acceptance tests (staging) | ${ACCEPTANCE} |

Run: ${RUN_URL}

The commit stage passed, so \`main\` is not broken — the artefacts exist and the
previous revision is still serving. Roll back with the **Rollback** workflow if a
staging revision took traffic before the failure.
EOF
)

existing=$(gh issue list --label deployment --state open --json number --jq '.[0].number // empty')
if [[ -n "$existing" ]]; then
  gh issue comment "$existing" --body "$body"
else
  gh issue create --title "Failed release: ${short} did not reach production" \
    --label deployment --body "$body"
fi
