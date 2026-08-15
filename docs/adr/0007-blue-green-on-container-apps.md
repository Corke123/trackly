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

- Demonstrates the thesis Ch 3.3 blue-green mechanism. **Rollback is manual**, not automated: it is a
  `workflow_dispatch` on `rollback.yaml`, and a human decides. An earlier version of this ADR claimed
  automated rollback, which was never built.

  Automating it is not simply unfinished work. The production identity's only federated credential
  subject is `environment:production` (ADR 0014), so any job touching production must declare that
  environment — and once required reviewers are installed there, an automatic rollback job would sit
  waiting for approval *during* an incident, which is worse than no automation. Under continuous
  delivery (Ch 3.2) a human is already at the controls when a release goes out, so the rollback decision
  costs nothing extra.

  The exposure this leaves is narrow. A failed deploy never shifts traffic at all (ADR 0015), so the only
  window is a release that passes its pre-traffic gate and then fails the staging acceptance suite. What
  is owed there is automated rollback of **staging**, where no approval gate applies, and it remains
  unbuilt.
- Requires services to be stateless and safe to run as two concurrent revisions briefly.
- The release half — new revision, provisioning wait, pre-traffic verification — runs concurrently for
  every changed service, so a four-service release costs the slowest service rather than their sum
  (ADR 0009). Traffic shifts stay sequential. One consequence: if *any* service fails its gate, no
  service's traffic is shifted, where the old sequential deploy had already cut over the services ahead
  of the failure. An environment left wholly on its previous revisions is the easier state to reason
  about during an incident.
- The health signal driving the gate is a Spring Boot Actuator endpoint (see ADR 0008 for
  why we avoid heavier observability for the gate).

> **Refined by [ADR 0015](0015-terraform-owns-infrastructure-cli-owns-revisions.md) and
> [ADR 0016](0016-scale-to-zero-cost-posture.md).** The "requires services to be stateless" condition is
> not fully met: the gateway and notification-service hold in-memory state, and the consequences are
> recorded in ADR 0016. Internally-ingressed services also get no pre-traffic HTTP health gate.
