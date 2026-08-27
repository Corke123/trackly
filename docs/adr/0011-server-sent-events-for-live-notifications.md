# 0011 — Server-sent events for live notifications

A user is told, while they are looking at the board, when a ticket is assigned to them or
when one of their tickets is moved by somebody else. notification-service exposes
`GET /activity/stream` as a **server-sent event** stream, one per signed-in user, reached
through the gateway on the SPA's own origin. Delivery is one-way and per-recipient: the
stream carries only activities addressed to the user whose token opened it, and the
recipient is taken from that token rather than from the request.

## Considered options

- **WebSocket** — bidirectional and heavier: a subprotocol, a handshake, its own auth story
  at the gateway, and a client library. Nothing here is sent from browser to server, so the
  second direction would be paid for and never used.
- **Polling `/activity`** — no new endpoint, but every client asks for a list it almost
  always already has, and "live" ends up meaning "within the poll interval".
- **SSE** — plain HTTP, so the gateway's token relay and session cookie work unchanged; the
  browser reconnects and resumes on its own via `Last-Event-ID`.

## Consequences

- The recipient of an event has to be derivable in the notification context, which is why
  `TicketMoved` carries the ticket's assignee and both events carry titles: notification
  owns no board data and cannot look them up.
- Connections are held in memory per replica (`ActivityStreamRegistry`), and the Service Bus
  subscription is shared by all replicas. **A replica delivers only to the browsers connected
  to it**: with more than one replica, an event consumed by replica A does not reach a user
  connected to replica B until that connection is re-established. This is accepted rather
  than solved — the activity table is the record, and a reconnecting client replays what it
  missed by id, so the gap closes on the next reconnect rather than losing a notification
  outright. A per-replica Service Bus subscription, or a shared fan-out through Redis, is the
  way out if single-replica delivery stops being good enough.
- The sentence a user reads is written once, at ingest, and stored next to the activity. The
  SPA renders it verbatim: wording board events is the notification context's job, and
  duplicating it in TypeScript would let the two drift.
- Each open stream holds a server thread for as long as it lasts, so connections carry a
  timeout and the browser is left to reconnect rather than being held indefinitely.

> **Amended by [ADR 0026](0026-board-changes-on-the-activity-stream.md).** The stream is no longer
> only per-recipient: alongside the activities addressed to one user it now carries `board-changed`,
> broadcast to everybody connected so that a board on screen catches up with what other people do to
> it. The replay contract above is untouched — a board change carries no event id.
