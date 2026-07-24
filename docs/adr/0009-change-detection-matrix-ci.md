# 0009 — Monorepo-aware CI: change-detection matrix + reusable workflow

The CI/CD entry point is a single orchestrator workflow that first runs a change-detection
job (path filtering) to determine which services (and the frontend / infra) actually
changed, then fans those — and only those — into a **matrix** that calls one parameterized
**reusable workflow** (`workflow_call`). Repeated step sequences (Java/Maven setup, Docker
build+scan+push, blue-green deploy) are factored into **composite actions**.

This is a deliberate use of the full GitHub Actions abstraction hierarchy from thesis Ch 5
(workflows → reusable workflows → composite actions, matrices, `needs` DAG, path-filtered
triggers) and preserves fast feedback (Ch 3.1.7) by never rebuilding unchanged services.

## Considered options

- **One workflow file per service** — explicit, but duplicates pipeline logic and shows
  less of the reusable/composite-action story.
- **Single monolithic workflow building everything every push** — simplest, but rebuilds
  unchanged services and violates fast feedback.

## Consequences

- Pipeline logic lives once in the reusable workflow; per-service workflows are thin.
- A change touching only shared paths (e.g. root config) may need explicit rules to decide
  what rebuilds.
