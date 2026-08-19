# 0020 — Delivery performance is measured, not asserted

The thesis argues its case for continuous delivery on the DORA research (Ch 3.1.7, Ch 3.3, ref [18]):
deployment frequency, lead time for changes, change failure rate and failed-deployment recovery time.
Until now nothing in this repository measured any of them, so every claim about the pipeline was a claim
about the *features it has* rather than the *performance it achieves* — which is exactly the substitution
the DORA programme exists to refute.

[`.github/actions/dora-metrics`](../../.github/actions/dora-metrics) computes the four metrics from the
repository's own history and [`dora.yaml`](../../.github/workflows/dora.yaml) runs it weekly.

## Where each number comes from

| Metric | Source | Definition used |
|---|---|---|
| Deployment frequency | Deployments API, `environment=production` | successful deployments ÷ weeks in window |
| Lead time for changes | commit `author.date` → deployment `success` status | median over successful releases |
| Change failure rate | deployment terminal states | non-`success` ÷ all terminal deployments |
| Recovery time | `ci.yaml` runs on `main` | median time a red mainline stayed red |

Two details are load bearing. `inactive` is **not** read as an outcome: GitHub stamps it on any deployment
a later one superseded, so every release older than the newest carries it, and treating it as terminal
silently reduced 19 measured releases to 3 during development. And lead time is measured from the commit's
**author** date rather than from the push, because the interval the metric is about starts when the work
was done, not when the developer got round to pushing it.

The report also splits the lead time into the part the pipeline owns (`in_progress` → `success`) and the
part it does not (review, and the wait on the production approval gate of ADR 0018). Without that split
the metric measures the author's habits as much as the pipeline, and no improvement to the pipeline can be
told from an improvement in how promptly someone clicks *Approve*.

## Why a JavaScript action

This is the repository's only **JavaScript action**; the other four are composite (ADR 0009) and one is a
container (ADR 0021). The choice is not decorative — a median over paginated API results with a per-metric
band classification is a program, and expressing it as composite `run:` steps would mean shell arithmetic
over `jq` output. The Node runtime the platform already provides makes it the cheapest of the three types
to start: no image pull, no container.

It has **no dependencies**, not even `@actions/core`. The usual objection to JavaScript actions is that a
JS action must ship its dependencies — the vendored `node_modules` directory the thesis notes in Ch 5.1.4.2
— and vendoring a tree of transitive packages into the repository that builds this project is precisely the
supply-chain surface ADR 0022 tries to reduce. Reading `INPUT_*` from the environment and appending to
`$GITHUB_OUTPUT` and `$GITHUB_STEP_SUMMARY` is what `@actions/core` does; doing it directly costs about
fifteen lines and removes the problem.

## Considered options

- **A dashboard service** (Grafana, or one of the commercial DORA products) — the metrics would be prettier
  and continuously visible, but they would live outside the repository, and the point here is that the
  measurement is version-controlled and reproducible by anyone who clones this.
- **Counting `deploy-production` job successes from the runs API** — works, but the Deployments API already
  models exactly this and additionally records the approval wait as a `waiting` → `in_progress` transition,
  which is the Ch 3.2 gate made visible.
- **Reporting only, no threshold** — rejected. `dora.yaml` fails when the overall band drops below *High*,
  on the same principle as the coverage gates of ADR 0010: a number nothing acts on stops being true.

## Consequences

- The measured result at the time of writing, over a 90-day window: **1.48 deployments/week** (High),
  **0.4 h** median lead time (Elite), **0 %** change failure rate (Elite), **1.23 h** median recovery
  (High) — overall **High**, bounded by deployment frequency.
- Deployment frequency is the weak metric and it is *not* a pipeline limitation: this is a one-person
  academic project, so there is nothing to deploy most weeks. Read as a capability rather than a habit, the
  0.4 h lead time is the number that says what the pipeline can do.
- The overall band is the *minimum* of the four, not an average. Averaging lets an Elite lead time hide a
  Low failure rate, and a delivery capability is bounded by its worst property.
- The window is a parameter, so the thesis can report a stable 90-day figure while a regression check can
  ask about the last 30 days.
