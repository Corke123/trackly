# 0012 — Staging and production share one data plane

`staging` and `production` are separate Container Apps environments, each with its own container apps,
managed identities and Key Vault — but they share **one** container registry, **one** PostgreSQL
Flexible Server, **one** Service Bus namespace and **one** Log Analytics workspace. Isolation between
them is logical: a database per service per environment (`board_db_staging`, `board_db_production`, …)
and a topic per environment (`board-events-staging`, `board-events-production`).

This extends ADR 0003, which contemplated only a single environment.

**This is a cost decision, not a security boundary, and it should not be described as one.** The
project is deployed on a personal subscription paid out of pocket, and duplicating the data plane would
roughly double the standing bill (~$21/month of shared resources) while adding nothing the thesis needs
to demonstrate. The second environment exists to demonstrate the Ch 3.2 delivery gate, not to survive a
production incident.

## Considered options

- **Fully isolated environments** — a resource group, PostgreSQL server, Service Bus namespace and Key
  Vault each. Correct, and what a real product would do. Roughly $42/month of standing cost and about
  twice the Terraform. Rejected on cost.
- **Production only** — cheapest of all, but the pipeline could then demonstrate continuous
  *deployment* (Ch 3.3) and never continuous *delivery* (Ch 3.2), because there would be no
  non-production environment to gate on. That gate is the distinction the thesis is built around.
  Rejected.
- **Ephemeral staging, created and destroyed per run** — near-zero staging cost and a strong Terraform
  demonstration, but a PostgreSQL Flexible Server alone takes about five minutes to provision, adding
  ten minutes to every pipeline run and several new ways for a live demo to fail. Rejected.

## Consequences

- **One PostgreSQL administrator login covers all six databases in both environments.** Anything able to
  read a staging container's configuration can reach production data. Managed-identity authentication
  (ADR 0013) reduces this in practice — each service authenticates as itself and owns only its own
  database — but the break-glass administrator remains shared.
- **One Burstable B1ms server serves both environments.** A staging load test starves production. There
  is no isolation of noisy neighbours because there are no neighbours to isolate.
- **One Service Bus namespace.** A staging misconfiguration that published to the production topic name
  would reach production consumers. Only the topic name separates them.
- **The connection budget is shared and tight.** B1ms allows 35 connections; six database-using
  instances at HikariCP's default pool of 10 would request 60. Every instance is capped at 3.
- The incremental cost of the second environment is close to zero, since its only dedicated resources
  are container apps that scale to zero.
- The documented upgrade path is a second `environments/` directory pointing at its own data plane; the
  environment module is already parameterised for it.
