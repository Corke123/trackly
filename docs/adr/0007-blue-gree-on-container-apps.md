# 0007 — Blue-green releases via Container Apps revisions

Production deploys use blue-green releases built on Azure Container Apps' native
multi-revision traffic splitting. The pipeline deploys the new image as a new revision
receiving **0%** of traffic, runs a smoke test against that revision's direct FQDN
(`/actuator/health`), and only then shifts **100%** of traffic to it. The previous
revision is kept warm so rollback is a single traffic re-point.

## Considered options

- **Canary (progressive traffic)** — richer, but needs metric-based gating automation for
  marginal additional thesis value.
- **Rolling replace of a single revision** — simplest, but demonstrates none of the
  Ch 3.3 fault-tolerance mechanisms.

## Consequences

- Directly demonstrates the thesis Ch 3.3 blue-green + automated-rollback concepts.
- Requires services to be stateless and safe to run as two concurrent revisions briefly.
- The health signal driving the gate is a Spring Boot Actuator endpoint (see ADR 0008 for
  why we avoid heavier observability for the gate).
