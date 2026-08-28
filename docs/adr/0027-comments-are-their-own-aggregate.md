# 0027 — A comment is its own aggregate, inside board-service

A Trackly ticket carries a title, a description and an assignee, and nothing a team can say to each
other about it. The discussion that a kanban board exists to focus therefore happens somewhere else,
and the board loses the context that explains why a ticket moved.

**Comments live in board-service, and a Comment is its own aggregate root that references its ticket
by id.** The API is nested under the ticket — `GET|POST /tickets/{ticketId}/comments` and
`DELETE /tickets/{ticketId}/comments/{commentId}` — and posting one publishes a `TicketCommented`
domain event through the outbox of [ADR 0004](0004-servicebus-topic-transactional-outbox.md).

## Considered options

- **A separate comment-service** — the decomposition question [ADR 0002](0002-service-decomposition.md)
  already answered for the four services this system has. A comment has no lifecycle of its own: it is
  created against a ticket, dies with it, and is never read except beside it. Splitting it out buys
  independent deployability for something that is never deployed for its own reasons, and costs a
  synchronous check that the ticket exists — or an eventually-consistent one that leaves comments on
  tickets that were deleted. It also multiplies the delivery surface: a fifth path filter, a fifth
  entry in `select-services.sh`, a fifth deploy step, two more Dependabot entries, another container
  app, another database. `pipeline-conventions` enforces every one of those, which makes the cost
  visible rather than cheap, and it is a cost paid to separate a table from the table it belongs to.
- **A `@MappedCollection` child of the `Ticket` aggregate** — the modelling that reads most naturally,
  and the one Spring Data JDBC punishes hardest. It rewrites a child collection by deleting and
  re-inserting it on every save of the root, and `moveTicket` and `assignTicket` save tickets
  constantly — a drag across a lane would rewrite the discussion on every ticket it renumbers. It is
  the same failure the swimlane reorder of `BoardRepositoryCustom` exists to avoid, and it would make
  loading a board load every comment on it.
- **Comments inside `BoardView`** — rejected for the same reason: the board view is fetched on every
  live `board-changed` ping (ADR 0026), and a thread is read when a ticket is opened, not when a board
  is drawn.

`Ticket` already sets the precedent this ADR follows. It is not nested inside `Board` either; it
carries `boardId` and `swimlaneId` as plain ids, because it moves between lanes and is written far
more often than the board is.

## Consequences

- A comment references `ticket_id` and nothing enforces that the ticket exists at the database level,
  exactly as `ticket` references `board_id`. Deleting a ticket deletes its comments explicitly, in the
  same transaction, because there is no cascade to do it.
- Reading a thread is one query on an indexed `ticket_id`. Reading a board costs nothing extra.
- The nested route means the gateway's existing `/tickets/**` predicate already carries it, so no
  gateway route and no `GatewayConfigTest` expectation changes. A flat `/comments/{id}` would have
  needed both.
- **`TicketCommented` must be added to two sealed hierarchies by hand.** There is no shared module
  (ADR 0024), so `sealed` gives exhaustiveness within a service and nothing across the topic. The
  producer compiles happily against a consumer that has never heard of the event.
- The deploy order of `deploy.yaml` is identity, board, notification, gateway — the producer of the
  new event goes live before its consumer, and `ActivityIngestService` rejects an event type it does
  not know. For the window between the two deploys, a comment posted in production records no
  Activity. Nothing in the pipeline exercises a mixed-version window, so no test reports this.
- A comment is addressed to the ticket's assignee, never to its own author, which is the Recipient
  rule of CONTEXT.md and the behaviour `ActivityIngestService.to(...)` already implements.
- `board-changed` announces that the board changed and never what it now looks like (ADR 0026). An
  open thread answers a change of type `TicketCommented` by re-fetching that ticket's comments, since
  `BoardView` does not carry them.
- Comments are immutable. There is no edit endpoint, and a correction is another comment. Deletion is
  the author's or an admin's.
