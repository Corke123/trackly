# 0018 — Repository configuration is a committed script, not a checklist

The GitHub settings this pipeline depends on — the ruleset on `main`, the issue labels the workflows
reference, Dependabot's security half, the read-only default token, and the environment rules that make
production a gated release — are applied by [`infra/harden-repo.sh`](../../infra/harden-repo.sh). It is
idempotent, section-invocable, and has a `--dry-run` mode that prints every mutating call it would make.

The README previously described these as four things to "set once in the GitHub UI". That description
was accurate about intent and wrong about outcome: **none of the four had been applied.** `main` had no
ruleset and no branch protection, so the `CI required` job that exists for the sole purpose of being a
required check ([ADR 0010](0010-two-stage-ci-pipeline.md)) was decorative. The `broken-build` label did
not exist, and `gh issue create --label` fails outright on a missing label, so the broken-mainline issue
that ADR 0010 calls "an assignable artefact" had never once been created. Dependabot's version updates
ran while its security alerts were off. The `production` environment had no protection rules at all,
which meant the pipeline was performing continuous deployment while three documents claimed it performed
continuous delivery.

That is the argument for this ADR. A manual checklist and an unapplied manual checklist are
indistinguishable from outside, and this repository is public, so the gap between what the documentation
claimed and what the repository did was verifiable by anyone who looked.

## Why not Terraform

The `integrations/github` provider can express all of this, and `infra/` already exists.

It would need a personal access token with `admin:repo` stored as a repository secret — a long-lived
credential of exactly the kind [ADR 0008](0008-oidc-federated-credentials.md) removed everywhere else,
and one whose blast radius is every repository the owner can administer. GitHub does not offer OIDC
federation for its own management API, so there is no short-lived equivalent.

It would also make Terraform a second owner of state that the GitHub UI edits directly. Adding a
reviewer through the web interface is the natural thing to do under time pressure, and Terraform's next
plan would propose reverting it. Configuration whose most convenient interface fights its declared
source is worse than configuration with no declared source.

A `gh`-based script keeps the operator's existing short-lived login as the only credential, and drifts
silently rather than destructively: re-running it re-asserts the intended state without proposing to
undo anything else.

## The cost of the approval gate

The `environments` section installs required reviewers on `production`, with `prevent_self_review` left
at its default so the repository owner can approve their own deployments. On a single-author thesis
project that is the only workable configuration, and it is honest about what the gate demonstrates: the
Ch 3.2 decision point and the human act of releasing, not segregation of duties.

The gate interacts with the concurrency design in a way worth recording, because it is not obvious and
it bites quietly.

A job awaiting environment approval **holds its concurrency group**, and by default only one run may be
pending per group — an additional pending run cancels the previous one. `deploy-staging`,
`deploy-production` and `infra.yaml`'s apply all share the `azure-container-apps` group, made
deliberately coarse so a Terraform apply and a blue-green release cannot interleave writes to the same
container apps. So an unapproved production deploy queues the next merge's staging deploy, and the merge
after that cancels the queued one — a deploy that never happens and never reports a failure.

The operating rule is therefore: **approve or reject each production deploy before merging the next pull
request.** The alternative — splitting the concurrency group per environment — is not available, because
Terraform's apply spans every environment and a job cannot hold two groups.

This is a real property of an approval gate on a pipeline with a shared resource, rather than a defect:
the gate converts human latency into skipped deployments. It is the kind of consequence that only shows
up once the gate is actually applied, which is itself an argument for applying it rather than describing
it.

## Considered options

- **Leave it as a README checklist.** Zero work, and the status quo this ADR exists to correct. The
  failure mode is silent and, as it turned out, had already happened.
- **A GitHub Actions workflow that applies the settings.** Attractive — configuration applied by the
  pipeline it configures. Rejected: it needs the same `admin:repo` PAT as the Terraform option, and a
  workflow that can rewrite its own repository's ruleset can also remove it, so the protection becomes
  self-modifying.
- **`gh` commands pasted into the README.** Better than prose, still not idempotent, and offers no
  dry run before touching a live repository.
- **Terraform with a PAT.** Covered above.

## Consequences

- The settings are reviewable as a diff, and section 6 can quote `--dry-run all` output as evidence of
  what the repository enforces rather than asserting it.
- Nothing verifies drift automatically. Re-running the script is the check, and the commands to confirm
  the result are printed when it finishes.
- The script needs admin permission, so it cannot run in CI under the default token. That is the point
  of the previous item.
- `allow_merge_commit` is disabled, because `required_linear_history` would otherwise reject a merge the
  UI had just offered. `allow_auto_merge` is enabled so a green pull request lands without a second
  visit, which is what keeps "0 required approvals" from meaning "merged before CI finished".
- The ruleset has **no bypass actors**, including the repository owner. Direct pushes to `main` are not
  possible for anyone; every change goes through a pull request. Emergency force-pushes are therefore
  unavailable and would require editing the ruleset first — accepted deliberately, since a bypass the
  author can always use is not a gate.
- `harden-repo.sh` deliberately does **not** set the `allowed_actions` allow-list or
  `sha_pinning_required`. Both depend on having enumerated every action the workflows use, and
  `sha_pinning_required` immediately invalidates the `actions/*` major-version tags that ADR 0010 chose
  to keep. They belong to a change that revisits that decision.
