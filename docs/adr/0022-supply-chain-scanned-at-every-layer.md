# 0022 — The supply chain is scanned at every layer it can be scanned

Before this decision the pipeline scanned exactly one layer: Trivy read the finished container image for
vulnerable OS packages and dependencies (ADR 0010), and SonarCloud read the source for quality and its own
taint rules (ADR 0019). Two things nothing looked at, both of which the thesis raises in Ch 5.4 and both of
which the SLSA and NIST SP 800-204D framings it cites (refs [31], [33]) treat as basic:

- **the application's own source, with a dataflow engine.** Trivy answers "is a known-vulnerable version of
  something present". It cannot answer "does a request parameter reach a query unsanitised". Sonar's taint
  analysis partially can, but it is a different engine with different coverage, and neither writes into
  GitHub code scanning where the finding sits next to the line that caused it.
- **what is actually in the artifact.** The build was attested (`attest-build-provenance`, ADR 0010) — the
  registry can prove *this pipeline* built the image — but nothing recorded *what the image contains*, so a
  question like "is that Netty version in production" was answerable only by pulling and re-scanning.

## What was added

| Layer | Tool | Where | Blocking |
|---|---|---|---|
| Source, dataflow | CodeQL `security-extended` | [`codeql.yaml`](../../.github/workflows/codeql.yaml) | no |
| Source, quality + taint | SonarCloud | in each build job (ADR 0019) | yes |
| Image packages | Trivy | `docker-build` composite | yes |
| Image contents | Trivy CycloneDX SBOM + `attest-sbom` | `docker-build` composite | n/a |
| Committed credentials | secret scanning + push protection | `harden-repo.sh` | at push |

CodeQL covers `java-kotlin`, `javascript-typescript` and — the one most CI/CD writing forgets — `actions`,
which analyses the workflows themselves. It runs on pull requests, on `main`, and on a **weekly schedule**,
because a query pack updated after a merge finds nothing until something re-runs. That is the use for cron
the thesis names in Ch 5.2.2, and this is the repository's honest instance of it.

`build-mode: none` for Java means CodeQL extracts without compiling. It costs some precision on generated
code and buys a scan that does not maintain a second, divergent build of four services.

Secret scanning is applied by `harden-repo.sh secret-scanning` rather than clicked, per ADR 0018, and
**push protection** is the half that matters: it refuses the push that contains a credential instead of
reporting it after the credential is in the history and has to be treated as compromised.

## Why CodeQL does not block

Every other gate here blocks. CodeQL deliberately does not, and the reason is the one from Ch 3.1.6: a gate
that is red for reasons nobody intends to act on teaches the team to ignore red. `security-extended` is the
wider query set on purpose — it is chosen to surface things worth reading, which necessarily includes things
that turn out not to be defects. Its findings land as code scanning alerts that are triaged and dismissed
with a reason, the same treatment ADR 0017 gives the accepted Sonar findings. If a class of finding proves
consistently actionable, promoting it to a required check is a one-line change.

## Considered options

- **CodeQL with `build-mode: manual`** — more precise for Java, and it would mean a second Maven build of
  every service inside the security workflow, diverging from the one the commit stage runs. Rejected: ADR
  0010's central claim is that this project builds each artifact exactly once.
- **Sonar alone** — already present and already blocking, but it is one vendor's engine, it does not write
  to code scanning, and it has no `actions` analysis at all.
- **Syft for the SBOM** — the conventional choice, and one more third-party action to pin and update. Trivy
  is already in the pipeline, already scanning the same image, and emits CycloneDX from the same invocation
  pattern.
- **Pushing attestations to the registry** (`push-to-registry: true`) — deferred. ACR Basic is the cost
  posture of ADR 0016 and the attestations are retrievable from the run.

## Consequences

- The SBOM is produced on pull requests too (as an artifact) and attested only on `main`, where an image is
  actually pushed. A reviewer can therefore see what a change adds to the image before it is merged.
- Three more workflow-triggered analyses means more Actions minutes. CodeQL on three languages with
  `build-mode: none` is the cheap end of this — under ten minutes total, off the critical path of `CI
  required`, and the schedule leg runs once a week.
- Secret scanning and push protection are free on public repositories and would be a paid feature on a
  private one; the choice to keep this repository public (a thesis artifact) is what makes them available.
- `attest-sbom` needs `attestations: write`, which the package job already requests for provenance.
