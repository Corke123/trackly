# 0021 — The pipeline's own invariants are checked by a container action

Adding a service to this monorepo means touching five files that no compiler relates to each other: the
service's `pom.xml` and the parent it inherits its coverage gate from (ADR 0010, ADR 0024), `ci.yaml`'s
path filter and `select-services.sh`'s list (change detection, ADR 0009), `deploy.yaml` (the release,
ADR 0007) and `dependabot.yml`. Miss one and nothing fails — the service simply stops being built, or
deployed, or updated, silently and for as long as nobody looks. The change-detection design of ADR 0009
makes this worse rather than better: a service with no path filter produces a green pipeline that built
nothing.

[`.github/actions/pipeline-conventions`](../../.github/actions/pipeline-conventions) asserts those
relationships, and the `lint` job in `ci.yaml` runs it on every pull request.

## What it asserts

- Every directory holding a `pom.xml` that is not a POM-only parent also holds a `Dockerfile` and `mvnw`,
  and declares — in its own POM or one it inherits from — a JaCoCo `check` whose weakest `<minimum>` is at
  least 0.85. The checker follows `relativePath` to read the chain, so the gate a service inherits from
  `trackly-shared` (ADR 0024) counts as its own.
- Every such service declares — again in its own POM or an inherited one — a `maven-checkstyle-plugin`
  that actually gates: a bound `check` goal, `violationSeverity` of `error`, a readable `configLocation`,
  and a `propertyExpansion` for every `severity` the ruleset defers to a property. That last assertion is
  the one that matters: `google_checks` defaults its severity to `warning`, so a POM that forgets to
  expand it prints every violation and exits zero ([ADR 0025](0025-formatting-is-gated-in-both-languages.md)).
- Every such service appears in `ci.yaml`'s path filters, in `select-services.sh`'s list, in
  `deploy.yaml`, and in `dependabot.yml` as both a `maven` and a `docker` ecosystem.
- Every POM that services inherit from appears in `ci.yaml`'s path filters. Inheritance makes the shared
  build part of all four builds while leaving it in a directory none of them watch, so without this a
  weakened gate reaches `main` having rebuilt nothing.
- A service whose tests mention Testcontainers has the `.ci/testcontainers-images.txt` the integration
  stage pre-pulls; a stale list only loses the optimisation, but a missing one loses it invisibly.
- Every workflow sets top-level `permissions` (or every job does), and every job that is not a
  `workflow_call` sets `timeout-minutes` — least privilege and a bounded job, from Ch 5.4.
- Every third-party `uses:` is pinned to a full commit SHA. This overlaps zizmor deliberately: zizmor is
  the authority, and the overlap is what proves the checker is looking at the same files the linters are.

It was verified against a deliberately broken copy of the repository — a weakened coverage minimum, a
deleted Dependabot entry, a removed `timeout-minutes` and an unpinned action — and reported all four. The
Checkstyle assertions were verified the same way against five more: a deleted plugin, an unbound `check`
goal, a removed `violationSeverity`, a removed `propertyExpansion` and a ruleset moved out from under
`configLocation`. The
two inheritance-aware assertions were verified the same way once ADR 0024 landed: weakening the
`<minimum>` in the parent is reported against all four services, and removing the parent's path filter is
reported against `ci.yaml`.

## Why a container action

This is the repository's only **Docker container action**, and the reason is the one the thesis gives in
Ch 5.1.4.1: the action carries its own runtime. The checker parses YAML, so it needs a YAML library; on a
GitHub-hosted runner it would inherit whatever Python and PyYAML the current `ubuntu-latest` image happens
to ship, and the runner image changes monthly without asking. A checker whose *own* behaviour drifts with
the runner image is a poor instrument for detecting drift. `python:3.13-alpine3.22` plus a pinned,
hash-verified `pyyaml` makes its verdict a property of this repository rather than of this month's runner
image. The pin is to the minor line, not to a digest, so the interpreter still moves at patch level when
the image is rebuilt; what it cannot do is arrive as a different Python.

The cost is honest and worth stating: a container action pays an image build (~20 s, cached across runs
within a job's lifetime but not across runs) that a composite action does not, and it is Linux-only. Both
are acceptable for a job that already runs `actionlint` and `zizmor` and only ever runs on `ubuntu-latest`.

## What the quality gate said about the checker

The first version of this action failed SonarCloud's quality gate: three findings on its `Dockerfile`
dropped the security rating on new code to **C**, and the gate demands **A** (ADR 0017). All three were
real, all three are now fixed rather than accepted, and the fixes are the reason this section exists — a
tool that checks this repository's hygiene is a poor place to be the exception to it.

| Rule | Finding | Fix |
|---|---|---|
| `docker:S6471` | the `python` image runs as `root` | `USER 1001` |
| `docker:S8541` | omitting `--only-binary :all:` allows an sdist's setup script to run during install | `--only-binary :all:` |
| `docker:S8544` | the dependency's resolved version is not locked | `--require-hashes` |

`USER 1001` is not an arbitrary number: it is the UID the runner owns the mounted workspace as. The checker
only reads the workspace, and matching that UID keeps the two files it appends to — `GITHUB_STEP_SUMMARY`
and `GITHUB_OUTPUT` — writable without root. Verified by running the image as UID 1001 against a read-only
workspace mount with both files owned by 1001: it read the tree, wrote the summary, and wrote its outputs.

Hash-pinning the dependency is the same argument the workflows make for pinning actions to commit SHAs: a
version number says which release was asked for, a hash says which bytes arrived. Both musllinux wheels are
listed so the image builds on an x86_64 runner and an arm64 workstation from one locked artefact set.
Verified negatively as well — replacing the hashes with wrong ones fails the build, so the pin is load
bearing rather than decorative.

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

- Two of the three action types of Ch 5.1.4 are present in one repository — four composite actions and one
  container action — chosen for their actual properties rather than for completeness. That is what makes
  them comparable in the thesis's practical chapter.
- Adding a fifth service now fails the pull request until it is wired in, with a message naming the file.
- The checker's thresholds are inputs, so tightening the coverage floor is a one-line change in `ci.yaml`
  rather than an edit to four POMs and the checker.
- `.github/actions/pipeline-conventions` is in `dependabot.yml` under `pip`, so its one library is updated
  on the same weekly cadence as everything else. The library lives in a `requirements.txt` rather than in
  an inline `pip install` in the `Dockerfile`, because Dependabot's pip ecosystem parses manifests and
  cannot see a version pinned in a `RUN` line — the entry would have been decorative, which is the failure
  mode ADR 0018 is about.

- It is **not** under `docker`, and that entry was removed after one week of it failing. The reasoning, and
  what to do if base-image automation is wanted later, is below.

## Why there is no `docker` Dependabot entry for this action

The entry was added with the action and failed on its first scheduled run. Dependabot resolved the base
image's latest tag as `3.14-alpine3.22`, tried to write `3.13-alpine3.22 → 3.14-alpine3.22`, and GitHub
rejected the pull request with `400 The request contains invalid or unauthorized changes`, surfaced by the
updater as `dependency_file_not_supported`.

**The cause was not established.** Two candidates, neither confirmed: GitHub may refuse a non-`github-actions`
ecosystem write inside `.github/`, or this may be the open upstream bug
[dependabot-core#11320](https://github.com/dependabot/dependabot-core/issues/11320), which reports the same
error for a `Containerfile` that is *not* under `.github/`. The repository offered no way to distinguish
them: every other `docker` entry tracks `eclipse-temurin:25-jre-alpine`, a floating tag that is always
current, so no other entry has ever attempted a write to compare against.

The entry was removed rather than diagnosed further, because on inspection it had **no useful work to do**:

- `python:3.13-alpine3.22` floats within its line, and this image is rebuilt from scratch on every pull
  request and never pushed to a registry, so interpreter and Alpine **patches arrive without a commit**.
  This is the opposite of the service images, which are built once, pushed, and then run for weeks
  (ADR 0010) — those genuinely need a commit to move.
- The only change a Dependabot commit could make is a **minor or major** line bump, and that is precisely
  what this container exists to prevent from happening unattended. A checker whose interpreter changes
  under it is a poor instrument for detecting change.

So a weekly-failing job was replaced by no job, and nothing is now unwatched that was watched before. If
base-image automation is wanted later, the ordered options are: pin the image by digest and let the `docker`
ecosystem propose digest bumps (which tests the `.github/` hypothesis directly, since it is the same write);
or move the action out of `.github/` — a local action may live at any path, so `uses: ./ci/pipeline-conventions`
works — remembering that the `shared` path filter in `ci.yaml` would then need to cover the new location or
editing the checker would silently stop rebuilding anything.
