# 0004 — Service Bus topic + transactional outbox for board events

board-service communicates with notification-service asynchronously over an Azure Service
Bus **topic** (`board-events`, Standard tier) using publish/subscribe, not synchronous
HTTP. To avoid the dual-write problem (a state change committed to the database but its
event lost before reaching the broker), board-service writes events to a **transactional
outbox** table in the same local transaction as the state change, and a poller relays
outbox rows to the topic with at-least-once delivery.

## Considered options

- **Basic tier + queue** — nearly free, but no pub/sub and no fan-out to future consumers.
- **Synchronous HTTP board→notification** — no broker, but couples the services and loses
  the decoupling/resilience the second service exists to demonstrate.

## Consequences

- notification-service handlers must be idempotent (at-least-once delivery).
- A Service Bus topic with a per-consumer subscription lets more consumers be added later
  without touching board-service.
- Standard tier's flat ~$10/mo is accepted as the cost of a faithful pub/sub demonstration
  (Ch 3.1.8 production parity, event-driven architecture).
> **Amended by [ADR 0013](0013-managed-identity-for-every-azure-dependency.md).** Producer and consumer
> authenticate with managed identities rather than a connection string, and the Azure topic TTL is `P7D`
> so events survive notification-service scaling to zero.
