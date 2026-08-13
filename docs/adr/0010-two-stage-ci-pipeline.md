# 0010 — Staged CI pipeline: fast commit build, slow verification behind it

The per-service pipeline (`.github/workflows/service-ci.yaml`, called by the change-detection orchestrator from ADR
0009) is split into a **commit stage** and two **later stages**:

| Stage             | Command                | Gate                                                     | Typical |
|-------------------|------------------------|----------------------------------------------------------|---------|
| Commit stage      | `./mvnw package`       | compile, unit tests, unit coverage ≥ 0.85                | ~2 min  |
| Integration stage | `./mvnw verify`        | Testcontainers integration tests, merged coverage ≥ 0.90 | ~6 min  |
| Package           | `docker build` + Trivy | image builds and scans clean                             | ~3 min  |

The commit stage needs no Docker and no external infrastructure, which is what keeps it inside the ten-minute feedback
budget (Ch 3.1.7). The integration stage starts a Postgres 17 container, a SQL Server container and the Service Bus
emulator — around 1.5 GB of images and ~90 s of container startup before a single assertion runs — so it cannot be part
of the fast gate. Both later stages depend on the commit stage rather than running from the start: a compile error must
not cost six minutes of image pulls.

The commit stage runs `package`, deliberately not `verify -DskipITs`. `package` stops before the
`verify` phase, so the merged-coverage rule is never evaluated against unit-only execution data. The integration stage
consequently runs the unit tests a second time (~15 s), which is required rather than wasteful: `jacoco.exec` must exist
in the same workspace as `jacoco-it.exec` for the merge to produce a real union.

## Coverage gates

Coverage is a build failure, not a report, so that "self-testing build" means something. Two rules live in each
service's `pom.xml` (not in the workflow, so a service can choose its own numbers without forking the pipeline). Both
`board-service` and `notification-service` currently gate at the same numbers, set from `board-service`'s measured
baselines:

- unit-only was LINE 93.0 % / BRANCH 93.8 % → gate at **0.85**
- merged unit ∪ integration was LINE 98.8 % / BRANCH 96.9 % → gate at **0.90**

There are no `<excludes>`. `BoardServiceApplication` and the `config` classes look uncovered to the unit tests and are
covered by the integration tests; the merged gate credits them honestly, which is a better answer than excluding them.
Removing a single service test class drops unit line coverage to 60 % and fails the build — verified, not assumed.

The JaCoCo agent's default `append=true` is turned off. Otherwise a local build without `clean`
gates on execution data accumulated from earlier runs and reports coverage the current tests do not actually provide.

## Visibility and provenance

Test totals, failing tests, per-class timings and coverage tables are rendered into each run's job summary by a
composite action using only `python3`; failing tests are additionally annotated inline on the pull request. A red `main`
opens (or comments on) a `broken-build` issue, because "fix broken builds immediately" needs an assignable artefact.
Mainline runs are never cancelled by the concurrency group, so a failure stays visible and attributable.

Every artifact carries its provenance: the `build-info` goal records the commit, build number and run URL at
`/actuator/info`. Nothing yet **asserts** that a running container reports the commit that built it, and version
information that is never checked tends to quietly stop being true — so that assertion is owed. It belongs to an
end-to-end test against a deployed staging environment rather than to a container the package stage starts and throws
away: the package stage cannot exercise the service's real dependencies (a Service Bus topic it subscribes to at
startup, a migrated database) without reassembling the compose topology per service, which duplicates the integration
stage's Testcontainers work at a lower fidelity than staging offers.

## Considered options

- **Single job running `./mvnw verify`** — simplest, and what the README previously described. But feedback on a typo
  would take eight minutes, which is the specific failure the deployment-pipeline practice exists to prevent.
- **Sharding the integration tests across a matrix** — both integration test classes share one Spring context and one
  set of containers, so sharding would double container startup rather than halve the wall clock.
- **Caching the Testcontainers images in the Actions cache** — `docker save` of the SQL Server image is ~700 MB–1 GB;
  cache download plus `docker load` is break-even at best against pulling from MCR, and it would evict the far more
  valuable `~/.m2` entry. A parallel pre-pull is used instead.
- **A pre-integration review gate** (required approvals ≥ 1) — rejected in favour of required status checks with **0
  required approvals**. GitHub can only enforce checks on pull requests, so a PR is required, but it can be self-merged
  the moment CI is green and reviewed afterwards. This keeps integration frequent, which is the point of the practice.

## Consequences

- Third-party actions are pinned to commit SHAs (`# vX.Y.Z` alongside), and Dependabot bumps the pins together with the
  comment. GitHub-owned `actions/*` stay on major tags.
- Continuous **delivery** is deliberately absent: there is no Azure subscription wired up yet. The seams that keep it
  cheap to add are the reusable workflow's `version`/`image-tag` outputs, the
  `docker-build` composite's `push`/`registry` inputs, and the stable `CI required` check name. No empty deploy workflow
  is committed, because scaffolding rots. ADR 0008 (OIDC) is unchanged.
- ADR 0009's `blue-green-deploy` composite is not built — it would have zero call sites today.
- Build *tooling* is not yet integrity-pinned: `distributionSha256Sum` is absent from the Maven wrapper properties, and
  the Dockerfile builder still uses the `maven:3.9-eclipse-temurin-25-alpine`
  image's own Maven rather than `./mvnw`, so the image build may use a different Maven version than CI and local builds
  do. Note if revisiting: `distributionSha256Sum` is the checksum of the `.zip`, and `mvnw` silently switches to the
  `.tar.gz` when `unzip` is missing, so an Alpine builder must install `unzip` in the same change.
- Trivy **blocks** the package stage (`exit-code: 1`). It ran report-only until the baseline was measured,
  and the measurement is why it could not simply be flipped: the images carried ten fixable HIGH findings
  — four Netty artifacts, the PostgreSQL driver and three Alpine packages — so enabling the gate first
  would have failed every build. The dependencies were pinned and the base packages upgraded until all
  four images scanned clean, and only then was the gate turned on.
- `ignore-unfixed: true` still applies, so only vulnerabilities with an available fix can fail a build. An
  unfixable base-image CVE cannot block delivery, which is what keeps a blocking gate usable rather than a
  standing outage.

> **Continued by [ADR 0015](0015-terraform-owns-infrastructure-cli-owns-revisions.md).** The seams named
> here are now used: `docker-build` pushes to ACR on `main`, and the deploy jobs live in `ci.yaml` outside
> the `CI required` job's `needs` so that check keeps its meaning. The `e2e-stack` suite runs against
> staging as the acceptance gate.
