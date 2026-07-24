# 0002 — Service decomposition: board + notification behind gateway + identity

Beyond the gateway (BFF) and identity (auth) services, the core domain is split into
**board-service** (owns the whole board aggregate: boards, swimlanes, tickets, assignment)
and **notification-service** (consumes board domain events and builds an activity feed).

We deliberately did *not* split tickets into their own service: a ticket only exists
within a board's aggregate, so a separate ticket-service would create an artificial
boundary and distributed-transaction pain. The board/notification split instead follows a
real bounded-context seam (Collaboration vs. Notification) connected by asynchronous
domain events — which is exactly what makes the async-messaging and independent-deploy
parts of the CI/CD story concrete.

## Consequences

- board-service is the single writer of board state; notification-service is read-only
  with respect to the board and derives its data from events.
- Two independently deployable domain services give the pipeline meaningful matrix/
  path-filter behaviour without inflating cost.