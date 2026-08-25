# 0001 — Monorepo for all Trackly components

We keep the SPA, all four backend services, the IaC, the pipelines, and the docs in a
single Git repository rather than one repo per service. This is the practical foundation
for the thesis's CI/CD demonstration: a single main branch and one place for every build
artifact (CI principle "single main repository", thesis Ch 3.1.1), while still allowing
independent per-service builds and deploys via path-based change detection in CI.

## Consequences

- CI must be monorepo-aware: only changed services build/deploy (see ADR 0009 / the
  change-detection workflow), otherwise fast feedback (Ch 3.1.7) is lost.
- Services stay independently deployable despite sharing a repo — no shared build.
> **Amended by [ADR 0024](0024-shared-build-in-a-parent-pom.md).** The services now inherit one parent
> POM holding the build they had four identical copies of. They stay independently buildable and
> deployable: the parent declares no modules, so each service still builds alone from its own directory.