# 0009 — Monorepo-aware CI: change-detection matrix + reusable workflow

The CI/CD entry point is a single orchestrator workflow that first runs a change-detection
job (path filtering) to determine which services (and the frontend / infra) actually
changed, then fans those — and only those — into a **matrix** that calls one parameterized
**reusable workflow** (`workflow_call`). Repeated step sequences (Java/Maven setup, Docker
build+scan+push, blue-green deploy) are factored into **composite actions**.

This is a deliberate use of the full GitHub Actions abstraction hierarchy from thesis Ch 5
(workflows → reusable workflows → composite actions, matrices, `needs` DAG, path-filtered
triggers) and preserves fast feedback (Ch 3.1.7) by never rebuilding unchanged services.

## The gateway is not a matrix leg

`gateway-service` calls the same reusable workflow, but from its own job rather than from the
matrix, because its image needs the SPA bundle that `build-spa` produces (ADR 0006) and that
dependency cannot be expressed from inside a matrix:

- **Matrix legs are not individually addressable in `needs:`.** Adding the dependency to the
  matrix job would make all four services wait on the front-end build.
- **`needs:` on a reusable-workflow caller waits for the whole called workflow**, not for one of
  its jobs. So a separate packaging job depending on the matrix would wait on every service's
  integration stage, costing the package stage the parallelism it has today.
- Downloading the artifact with no dependency edge at all is a race.

This is a real dent in the "one matrix, one reusable workflow" shape above, and it is recorded
rather than hidden. The pipeline logic still lives once — `build-gateway` passes different inputs
to the same `service-ci.yaml`, it does not fork it. `select-services.sh` reports `gateway`
separately from `backend-services` so both jobs key off one change-detection pass, and `build-spa`
keys off `gateway` rather than off the client, so a gateway-only Java change still gets a bundle.

## Considered options

- **One workflow file per service** — explicit, but duplicates pipeline logic and shows
  less of the reusable/composite-action story.
- **Single monolithic workflow building everything every push** — simplest, but rebuilds
  unchanged services and violates fast feedback.

## Consequences

- Pipeline logic lives once in the reusable workflow; per-service workflows are thin.
- A change touching only shared paths (e.g. root config) may need explicit rules to decide
  what rebuilds.
