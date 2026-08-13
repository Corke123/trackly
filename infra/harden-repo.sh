#!/usr/bin/env bash

set -euo pipefail

DRY_RUN=0
SECTIONS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=1 ;;
    labels | merge-settings | workflow-permissions | ruleset | dependabot | environments) SECTIONS+=("$1") ;;
    all) SECTIONS=(labels merge-settings workflow-permissions ruleset dependabot environments) ;;
    -h | --help)
      cat <<'USAGE'
Usage: harden-repo.sh [--dry-run] <section...|all>

Sections, in the order they should be applied:
  labels                issue labels the workflows reference
  merge-settings        squash-only merges, branch deletion, auto-merge
  workflow-permissions  read-only default token for every workflow
  ruleset               the ruleset on the default branch
  dependabot            security alerts and automated security fixes
  environments          deployment branch policies, and required reviewers on production

--dry-run prints every mutating call without making it. Reads still happen.
USAGE
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
  shift
done

if [[ ${#SECTIONS[@]} -eq 0 ]]; then
  echo "Nothing to do. Pass one or more sections, or 'all'. See --help." >&2
  exit 1
fi

RULESET_NAME="${RULESET_NAME:-default branch}"
REQUIRED_CHECK="${REQUIRED_CHECK:-CI required}"

gh auth status >/dev/null 2>&1 || {
  echo "Not logged in to GitHub. Run: gh auth login" >&2
  exit 1
}

REPO="${GITHUB_REPOSITORY:-$(gh repo view --json nameWithOwner --jq .nameWithOwner)}"
DEFAULT_BRANCH=$(gh api "repos/${REPO}" --jq .default_branch)

if [[ $(gh api "repos/${REPO}" --jq .permissions.admin) != "true" ]]; then
  echo "Admin permission on ${REPO} is required to change its settings." >&2
  exit 1
fi

echo "==> ${REPO} (default branch ${DEFAULT_BRANCH})"
(( DRY_RUN )) && echo "    dry run: no changes will be made"

api() {
  local method="$1" path="$2" body="${3:-}"

  if [[ -n "$body" ]]; then
    if (( DRY_RUN )); then
      echo "    [dry-run] gh api -X ${method} ${path} --input -"
      printf '%s\n' "$body" | sed 's/^/              /'
      return 0
    fi
    printf '%s' "$body" | gh api -X "$method" "$path" --input - >/dev/null
  else
    if (( DRY_RUN )); then
      echo "    [dry-run] gh api -X ${method} ${path}"
      return 0
    fi
    gh api -X "$method" "$path" --silent
  fi
}

label() {
  local name="$1" color="$2" description="$3"

  if (( DRY_RUN )); then
    echo "    [dry-run] gh label create ${name} --color ${color} --force"
    return 0
  fi
  gh label create "$name" --color "$color" --description "$description" --force >/dev/null
  echo "    ${name}"
}

do_labels() {
  echo "==> Labels"
  label broken-build B60205 "The commit stage is red on the default branch"
  label deployment 1D76DB "A release to staging or production failed"
  label dependencies 0366D6 "Dependency version or security update"
  label ci 5319E7 "Pipeline definition and workflows"
  label docker 1D63ED "Container images and base images"
  label trackly-client C5DEF5 "Angular single-page application"
  for service in board notification identity gateway; do
    label "${service}-service" C5DEF5 "${service}-service"
  done
}

do_merge_settings() {
  echo "==> Merge settings"
  api PATCH "repos/${REPO}" '{
    "allow_squash_merge": true,
    "allow_rebase_merge": true,
    "allow_merge_commit": false,
    "delete_branch_on_merge": true,
    "allow_auto_merge": true
  }'
  echo "    squash and rebase only, branch deleted on merge, auto-merge available"
}

do_workflow_permissions() {
  echo "==> Workflow permissions"
  api PUT "repos/${REPO}/actions/permissions/workflow" '{
    "default_workflow_permissions": "read",
    "can_approve_pull_request_reviews": false
  }'
  echo "    GITHUB_TOKEN is read-only by default; jobs request more where they need it"
}

do_ruleset() {
  echo "==> Ruleset '${RULESET_NAME}'"

  local body
  body=$(
    cat <<JSON
{
  "name": "${RULESET_NAME}",
  "target": "branch",
  "enforcement": "active",
  "bypass_actors": [],
  "conditions": {
    "ref_name": { "include": ["~DEFAULT_BRANCH"], "exclude": [] }
  },
  "rules": [
    { "type": "deletion" },
    { "type": "non_fast_forward" },
    { "type": "required_linear_history" },
    {
      "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 0,
        "dismiss_stale_reviews_on_push": false,
        "require_code_owner_review": false,
        "require_last_push_approval": false,
        "required_review_thread_resolution": false,
        "allowed_merge_methods": ["squash", "rebase"]
      }
    },
    {
      "type": "required_status_checks",
      "parameters": {
        "strict_required_status_checks_policy": true,
        "do_not_enforce_on_create": false,
        "required_status_checks": [
          { "context": "${REQUIRED_CHECK}" }
        ]
      }
    }
  ]
}
JSON
  )

  local existing
  existing=$(gh api "repos/${REPO}/rulesets" --jq ".[] | select(.name==\"${RULESET_NAME}\") | .id" 2>/dev/null || true)

  if [[ -n "$existing" ]]; then
    api PUT "repos/${REPO}/rulesets/${existing}" "$body"
    echo "    updated ruleset ${existing}"
  else
    api POST "repos/${REPO}/rulesets" "$body"
    echo "    created"
  fi
  echo "    pull request required, 0 approvals, '${REQUIRED_CHECK}' must pass, linear history, no force-push"
}

do_dependabot() {
  echo "==> Dependabot"
  api PUT "repos/${REPO}/vulnerability-alerts"
  echo "    security alerts enabled"
  api PUT "repos/${REPO}/automated-security-fixes"
  echo "    automated security fixes enabled"
}

do_environments() {
  echo "==> Environments"

  local reviewer_id
  reviewer_id=$(gh api "repos/${REPO}" --jq .owner.id)

  for env_name in staging production; do
    local body
    if [[ "$env_name" == production ]]; then
      body=$(
        cat <<JSON
{
  "prevent_self_review": false,
  "reviewers": [ { "type": "User", "id": ${reviewer_id} } ],
  "deployment_branch_policy": {
    "protected_branches": false,
    "custom_branch_policies": true
  }
}
JSON
      )
    else
      body=$(
        cat <<'JSON'
{
  "deployment_branch_policy": {
    "protected_branches": false,
    "custom_branch_policies": true
  }
}
JSON
      )
    fi

    api PUT "repos/${REPO}/environments/${env_name}" "$body"

    local policies
    policies=$(gh api "repos/${REPO}/environments/${env_name}/deployment-branch-policies" \
      --jq '.branch_policies[].name' 2>/dev/null || true)

    if grep -qxF "$DEFAULT_BRANCH" <<<"$policies"; then
      echo "    ${env_name}: deployable from ${DEFAULT_BRANCH} only"
    else
      api POST "repos/${REPO}/environments/${env_name}/deployment-branch-policies" \
        "{\"name\":\"${DEFAULT_BRANCH}\",\"type\":\"branch\"}"
      echo "    ${env_name}: restricted to ${DEFAULT_BRANCH}"
    fi
  done

  echo "    production requires a review before it deploys"
}

for section in "${SECTIONS[@]}"; do
  case "$section" in
    labels) do_labels ;;
    merge-settings) do_merge_settings ;;
    workflow-permissions) do_workflow_permissions ;;
    ruleset) do_ruleset ;;
    dependabot) do_dependabot ;;
    environments) do_environments ;;
  esac
done

echo
if (( DRY_RUN )); then
  echo "Dry run complete. Nothing was changed."
else
  cat <<EOF
Done. Verify with:

  gh api repos/${REPO}/rulesets --jq '.[].name'
  gh api repos/${REPO}/environments --jq '.environments[]|{name,rules:[.protection_rules[]?.type]}'
  gh api repos/${REPO}/vulnerability-alerts -i | head -1
  gh label list --limit 30
EOF
fi
