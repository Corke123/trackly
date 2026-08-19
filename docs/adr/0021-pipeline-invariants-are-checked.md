# 0021 — The pipeline's own invariants are checked by a container action

Adding a service to this monorepo means touching five files that no compiler relates to each other: the
service's `pom.xml` (its coverage gate, ADR 0010), `ci.yaml`'s path filter and `select-services.sh`'s list
(change detection, ADR 0009), `deploy.yaml` (the release, ADR 0007) and `dependabot.yml`. Miss one and
nothing fails — the service simply stops being built, or deployed, or updated, silently and for as long as
nobody looks. The change-detection design of ADR 0009 makes this worse rather than better: a service with
no path filter produces a green pipeline that built nothing.

[`.github/actions/pipeline-conventions`](../../.github/actions/pipeline-conventions) asserts those
relationships, and the `lint` job in `ci.yaml` runs it on every pull request.

## What it asserts

- Every directory holding a `pom.xml` also holds a `Dockerfile` and `mvnw`, and its POM declares a JaCoCo
  `check` whose weakest `<minimum>` is at least 0.85.
- Every such service appears in `ci.yaml`'s path filters, in `select-services.sh`'s list, in
  `deploy.yaml`, and in `dependabot.yml` as both a `maven` and a `docker` ecosystem.
- A service whose tests mention Testcontainers has the `.ci/testcontainers-images.txt` the integration
  stage pre-pulls; a stale list only loses the optimisation, but a missing one loses it invisibly.
- Every workflow sets top-level `permissions` (or every job does), and every job that is not a
  `workflow_call` sets `timeout-minutes` — least privilege and a bounded job, from Ch 5.4.
- Every third-party `uses:` is pinned to a full commit SHA. This overlaps zizmor deliberately: zizmor is
  the authority, and the overlap is what proves the checker is looking at the same files the linters are.

It was verified against a deliberately broken copy of the repository — a weakened coverage minimum, a
deleted Dependabot entry, a removed `timeout-minutes` and an unpinned action — and reported all four.

## Why a container action

This is the repository's only **Docker container action**, and the reason is the one the thesis gives in
Ch 5.1.4.1: the action carries its own runtime. The checker parses YAML, so it needs a YAML library; on a
GitHub-hosted runner it would inherit whatever Python and PyYAML the current `ubuntu-latest` image happens
to ship, and the runner image changes monthly without asking. A checker whose *own* behaviour drifts with
the runner image is a poor instrument for detecting drift. `python:3.13-alpine3.22` plus a pinned
`pyyaml==6.0.3` in an image that Dependabot watches makes its verdict a property of this repository, and a
reproducible one: two runs a month apart reach the same conclusion about the same tree.

The cost is honest and worth stating: a container action pays an image build (~20 s, cached across runs
within a job's lifetime but not across runs) that a composite action does not, and it is Linux-only. Both
are acceptable for a job that already runs `actionlint` and `zizmor` and only ever runs on `ubuntu-latest`.

## Considered options

- **A composite action running `python3`** — what `maven-report-summary` does, and what this started as. It
  works today and would keep working until the day the runner image moves and the failure looks like a
  repository problem rather than a tooling problem.
- **A shell script called from the workflow** — the repository's usual choice for pipeline logic
  (`select-services.sh`, `rollback.sh`). Rejected here because the checks parse YAML structurally: reading
  `dependabot.yml`'s `updates[].directory` with `grep` would pass on a commented-out entry.
- **Trusting review** — the substance of ADR 0018's argument, applied to itself: a convention nobody
  re-checks is indistinguishable from a convention nobody follows.

## Consequences

- The three action types of Ch 5.1.4 are now all present in one repository — four composite actions, one
  JavaScript action (ADR 0020) and one container action — chosen for their actual properties rather than
  for completeness. That is what makes them comparable in the thesis's practical chapter.
- Adding a fifth service now fails the pull request until it is wired in, with a message naming the file.
- The checker's thresholds are inputs, so tightening the coverage floor is a one-line change in `ci.yaml`
  rather than an edit to four POMs and the checker.
- `.github/actions/pipeline-conventions` is in `dependabot.yml` under both `docker` and `pip`, so its base
  image and its one library are updated on the same weekly cadence as everything else. The library lives in
  a `requirements.txt` rather than in an inline `pip install` in the `Dockerfile`, because Dependabot's pip
  ecosystem parses manifests and cannot see a version pinned in a `RUN` line — the entry would have been
  decorative, which is the failure mode ADR 0018 is about.
