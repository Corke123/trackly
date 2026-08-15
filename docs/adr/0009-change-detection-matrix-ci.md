# 0009 — Monorepo-aware CI: change-detection matrix + reusable workflow

The CI/CD entry point is a single orchestrator workflow that first runs a change-detection
job (path filtering) to determine which services (and the frontend / infra) actually
changed, then fans those — and only those — into a **matrix** that calls one parameterized
**reusable workflow** (`workflow_call`). Repeated step sequences (Java/Maven setup, Docker
build+scan+push, test-report summaries) are factored into **composite actions**.

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

## The blue-green deploy is a script, not a composite action

The deploy was four `blue-green-deploy` composite invocations, one per service, and they ran in
sequence because **composite steps cannot run concurrently** — a composite action is a step list, and
GitHub runs a job's steps one at a time. Measured on staging that cost 5m07s of wall clock (identity
1:56, board 0:41, notification 0:37, gateway 1:53) for work whose critical path is 2m20s. The composite
has been replaced by [`.github/scripts/blue-green-deploy.sh`](../../.github/scripts/blue-green-deploy.sh),
which forks one worker per service and waits on them, matching the extraction already done for the infra
and rollback workflows.

This costs a call site of the abstraction hierarchy this ADR is partly about, and no arrangement keeps
both — the concurrency and the composite are mutually exclusive. `docker-build`, `setup-java-maven` and
`maven-report-summary` still carry that half of the story, and the script is testable outside Actions,
which no composite is.

A `strategy.matrix` over the four services was the obvious alternative and is wrong here for three
reasons, each about the deploy job specifically rather than about matrices:

- The `azure-container-apps` concurrency group is shared with `infra.yaml` (ADR 0018) so Terraform and a
  release cannot interleave. GitHub keeps one running and one pending member per group, so matrix legs
  in that group would cancel each other.
- `environment: ${{ inputs.environment }}` is load-bearing: the production identity's only federated
  credential subject is `environment:production` (ADR 0014). Four jobs means four pending deployments,
  and so four production approvals for one release.
- Matrix job outputs are last-writer-wins, which would break the `gateway-url` output the acceptance
  suite runs against.

Fanning out *inside* the one guarded job keeps one lock, one approval and one set of outputs. Two things
the script has to get right, both invisible until they bite: concurrent `az` processes share the MSAL
token cache under `~/.azure` and corrupt each other, so each worker gets its own `AZURE_CONFIG_DIR`
copied from the logged-in one; and a backgrounded worker's failure is only observable through `wait`, so
exit statuses are aggregated explicitly and each service's log is replayed into its own `::group::`
rather than interleaved live.

Traffic shifts stay sequential, in the order the README documents. They are ~2s control-plane writes, so
ordering them is free.

## Considered options

- **One workflow file per service** — explicit, but duplicates pipeline logic and shows
  less of the reusable/composite-action story.
- **Single monolithic workflow building everything every push** — simplest, but rebuilds
  unchanged services and violates fast feedback.

## Consequences

- Pipeline logic lives once in the reusable workflow; per-service workflows are thin.
- A change touching only shared paths (e.g. root config) may need explicit rules to decide
  what rebuilds.
