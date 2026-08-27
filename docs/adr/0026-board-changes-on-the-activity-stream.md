# 0026 — The activity stream also carries board changes

Trackly is one board a team works on together (CONTEXT.md), but the board on screen was a snapshot
taken when the page loaded. Two users looking at the same board saw different boards the moment one
of them moved a ticket, and the only way to find out was to reload. For a tool whose whole subject is
a shared board, that is the wrong default.

The activity stream of [ADR 0011](0011-server-sent-events-for-live-notifications.md) already delivers
board news to a signed-in user, but only the news addressed to them personally: an Activity carries a
single Recipient, most Activities have none, and the actor is never their own recipient. The move that
leaves the other user stale — somebody dragging an unassigned ticket — puts nothing on any wire.

**notification-service broadcasts a second event, `board-changed`, on that same stream.** It is
derived from every board domain event it ingests, addressed to nobody, and sent to every open stream.
The SPA answers it by re-fetching `GET /boards/{id}`.

## Considered options

- **Polling `/boards/{id}`** — no new event and no new plumbing, but every client asks for a board it
  almost always already has, and "live" ends up meaning "within the poll interval". This is the same
  argument ADR 0011 made against polling `/activity`.
- **A second SSE endpoint in board-service** — closest to where the change actually happens, and it
  would not stretch the notification context. It costs a second connection per tab, a second gateway
  route, and a second copy of the registry, the timeout and the heartbeat, for a signal that
  notification-service already receives from the same domain events.
- **Pushing the change itself rather than a ping** — sending the moved ticket would spare the client a
  fetch, but the notification context owns no board data and cannot describe a board; ADR 0011 already
  records that it has to be told titles because it cannot look them up. Modelling the board there to
  save one GET would duplicate board-service in the wrong context.
- **The existing `activity` event** — rejected outright: it is addressed to one Recipient by design,
  and most board changes have none.

## Consequences

- The event carries `boardId`, `type`, `actorId` and `occurredAt` — enough to decide whether to react,
  nothing about what changed. Reacting costs one `GET /boards/{id}` per connected client per change.
- **It carries no SSE id.** `Last-Event-ID` addresses the Activity replay of ADR 0011, which resumes by
  activity id; an unidentified event cannot become a resume point and leaves that contract alone. The
  price is that a board change is never replayed, so the client refreshes whenever the stream opens
  again after having been connected, whatever reopened it.
- A user does not refresh on their own changes: the SPA drops a change whose `actorId` is the signed-in
  user, because the local board already shows it.
- A refresh is held back while a write of the user's own is in flight, and runs once when the last one
  lands. Without that, a refresh can arrive between an optimistic move and its response and briefly
  show the board without the move the user just made.
- Refreshes are collapsed into one fetch per 250 ms window, so a burst of events costs one request.
- The stream is no longer strictly per-recipient, which is what ADR 0011 said it was. What is delivered
  is still derived from the recipient's own token for `activity`; `board-changed` is broadcast, and
  carries nothing a user on the board may not see.
- **Only ticket changes travel this way.** board-service publishes domain events for tickets only, so
  adding, deleting or reordering a swimlane, and renaming the board, still need a reload. Making those
  live means new domain events on the topic, in both contexts.
- Liveness is bounded by the outbox relay poll (2s by default) plus the Service Bus hop, so this is
  "within a few seconds", not instant.
- The per-replica delivery caveat of ADR 0011 applies unchanged, and for the same reason: the registry
  is in memory and the Service Bus subscription is shared. A broadcast reaches the browsers connected
  to the replica that consumed the event. `max_replicas = 1` keeps that theoretical for now.
- A refresh replaces the whole board object, so every lane re-renders — the one place in the SPA that
  happens. Lanes and tickets are tracked by id, so a drag in progress survives unless the dragged
  ticket itself was changed from elsewhere.
