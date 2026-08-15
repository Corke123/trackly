#!/usr/bin/env bash
set -euo pipefail

: "${RUN_URL:?RUN_URL must be set to the failing run}"

short="${GITHUB_SHA:0:7}"
body=$(cat <<EOF
CI failed on \`main\` at \`${GITHUB_SHA}\` (@${GITHUB_ACTOR}).

Run: ${RUN_URL}

Fixing a broken mainline takes priority over new work: revert the offending commit,
or fix forward if the fix is obvious and small.
EOF
)

existing=$(gh issue list --label broken-build --state open --json number --jq '.[0].number // empty')
if [[ -n "$existing" ]]; then
  gh issue comment "$existing" --body "$body"
else
  gh issue create --title "Broken mainline: CI failed at ${short}" \
    --label broken-build --body "$body"
fi
