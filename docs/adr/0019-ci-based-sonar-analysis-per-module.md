# 0019 — SonarCloud analysis runs in the pipeline, one project per module

SonarCloud analysed this repository through **Automatic Analysis**: it read the code from GitHub on its
own schedule and decorated pull requests with the result. That arrangement produced the findings recorded
in [ADR 0017](0017-accepted-static-analysis-findings.md), and it has two limits that matter.

It cannot see coverage. Automatic Analysis never runs the build, so no JaCoCo or lcov report reaches it,
and every module showed 0% — while [ADR 0010](0010-two-stage-ci-pipeline.md)'s whole argument is that
coverage is gated rather than reported.

It cannot block anything. It runs beside the pipeline rather than inside it, so a failing quality gate was
a comment on a pull request that `CI required` never consulted, and on `main` it had no relationship at
all to `deploy-staging`. The README claimed the gate blocked merges; the ruleset required one check, `CI
required`, and Sonar was not part of it. That is the same gap between claimed and applied configuration
that [ADR 0018](0018-repository-configuration-is-a-committed-script.md) exists to close.

Analysis now runs in CI, with `sonar.qualitygate.wait=true`. The scanner polls until the gate is computed
and exits non-zero when it fails, so the gate fails the job that produced the code, which fails `CI
required`, which the ruleset already requires.

## Why six projects rather than one

[ADR 0009](0009-change-detection-matrix-ci.md) builds only what changed. A SonarCloud project holds the
*complete* state of what it analyses, so a scan that covers only `board-service` tells the project that
every file belonging to the other three services no longer exists — closing their issues and discarding
their coverage until some later run happened to touch them. One project and change-detected builds are
therefore incompatible: keeping a single project means building all four services and the client on every
pull request, which is the cost ADR 0009 was written to avoid.

The repository is public, so SonarCloud's monorepo support — several projects bound to one repository — is
free here. Each module gets a project whose key is `Corke123_trackly_` followed by its directory name, and
each scans itself in the job that already builds it. A gate failure fails that module's job and nothing
else.

## Why `Corke123_trackly` survives as the repository project

The four findings [ADR 0017](0017-accepted-static-analysis-findings.md) accepts are all in `infra/**`, and
they are marked **Safe / Won't fix** against issue keys held by the project `Corke123_trackly`. That ADR
records the cost of losing them: "it recurs if the analysis is reset or the project re-imported."

So the existing project is not replaced. It is narrowed to what no module project owns — `infra/`,
`.github/`, `local/`, the compose file and the four Dockerfiles — and switched from automatic to CI-based
analysis. The Terraform paths are unchanged, so the four marks hold, and the Java and TypeScript files it
used to carry move to the module projects.

The Dockerfiles sit here rather than with their services because SonarScanner for Maven derives its file
list from the POM and supports files outside the source roots only partially. Overriding `sonar.sources`
per service would move them, at the risk of disturbing how the scanner locates compiled classes for Java
analysis. They are read by the same CLI scanner that reads `.github/`, which has no such constraint.

## The missing token is a skip, not a failure

Dependabot pull requests and any future fork contribution do not receive repository secrets. A scan step
that assumed `SONAR_TOKEN` would fail every one of them on a credential GitHub deliberately withholds, so
each scan is guarded on the token being present and writes to the job summary when it is not.

This is a real hole in the gate: a pull request that arrives without the token is not analysed. It is
narrow — Dependabot changes only dependency manifests — and closable by adding `SONAR_TOKEN` to the
repository's Dependabot secrets, which is a separate store from Actions secrets.

## Considered options

- **Keep Automatic Analysis and make its check required.** The check exists and the ruleset could demand
  it. Rejected: it still cannot see coverage, so the gate would grade new code on a project that believes
  nothing is tested, and its timing is Sonar's rather than the pipeline's — the check can arrive after the
  merge button is available.
- **One project, full build on every pull request.** Simple, and preserves ADR 0017's marks untouched.
  Rejected: it discards change detection and puts four Testcontainers-backed integration runs on every
  pull request, including ones that change a single line of TypeScript.
- **New projects for everything, including the infrastructure.** Cleaner keys. Rejected for the cost in
  ADR 0017: four findings would need re-reviewing and re-marking by hand, and the argument for each is
  already written down.
- **Scan in the commit stage rather than the integration stage.** Faster feedback. Rejected: only the
  integration stage has the merged JaCoCo report, so the coverage Sonar saw would exclude every
  integration test — understating it in exactly the way Automatic Analysis already did.

## Consequences

- Six projects have six quality gates. A merge is blocked when any of them fails, and each failure names
  the module it came from.
- The README's two badges track `Corke123_trackly`, which now measures infrastructure and workflows only.
  A module's gate is visible on its own project page and on the pull request, not in the README.
- Coverage reaches Sonar for the first time, so each module project's first analysis will move from 0% to
  its real figure. New-code conditions on coverage will behave meaningfully from that point rather than
  failing everything or nothing.
- Analysis is bound to the integration stage, so a service configured with `run-integration-tests: false`
  is not analysed at all. Nothing sets that today.
- `fetch-depth: 0` is now required on the scanning checkouts. A shallow clone leaves the scanner unable to
  attribute lines to commits, which is what "new code" means to a quality gate.
