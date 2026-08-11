# 0003 — One PostgreSQL server, one database per service

Each service owns its own logical database (`board_db`, `notification_db`, `identity_db`)
with no cross-service database access — the microservice data-ownership rule. To keep cost
low, all three databases live on a **single** Azure Database for PostgreSQL Flexible Server
(Burstable `B1ms`) rather than a server per service.

## Considered options

- **Server per service** — textbook isolation, but ~3× the cost and IaC/backup surface.
  Rejected as overkill for a showcase.
- **Shared database, schema per service** — cheapest, but blurs ownership and invites
  cross-service joins, weakening the microservice story we are demonstrating.

## Consequences

- Isolation is logical, not physical: a noisy neighbour could affect others. Acceptable
  for a demo; noted as a scaling limitation in Section 6.
- Each service manages its own schema migrations (Flyway) against its own database only.
> **Amended by [ADR 0012](0012-shared-data-plane-across-environments.md).** The single server is now
> shared by `staging` and `production` as well, giving six databases rather than three.
