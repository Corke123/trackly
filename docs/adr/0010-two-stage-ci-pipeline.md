# 0010 — Staged CI pipeline: fast commit build, slow verification behind it

The per-service pipeline (`.github/workflows/service-ci.yaml`, called by the change-detection orchestrator from ADR
0009) is split into a **commit stage** and two **later stages**:

| Stage             | Command                             | Gate                                                     | Typical |
|-------------------|-------------------------------------|----------------------------------------------------------|---------|
| Commit stage      | `./mvnw package`                    | compile, unit tests, unit coverage ≥ 0.85                | ~2 min  |
| Integration stage | `./mvnw verify`                     | Testcontainers integration tests, merged coverage ≥ 0.90 | ~6 min  |
| Package           | `docker build` + Trivy + smoke test | image builds, starts, and reports its own revision       | ~3 min  |

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

Coverage is a build failure, not a report, so that "self-testing build" means something. Two rules live in
`board-service/pom.xml` (not in the workflow, so a future service can choose its own numbers without forking the
pipeline), set from measured baselines:

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

Every artefact carries its provenance: the `build-info` goal records the commit, build number and run URL, and the
package stage **asserts** that the running container reports the commit that built it at `/actuator/info`. Version
information that is never checked tends to quietly stop being true.

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
- Trivy runs in report-only mode (`exit-code: 0`) until the baseline finding count for
  `eclipse-temurin:25-jre-alpine` is known; flipping it to `1` is a one-input change.
