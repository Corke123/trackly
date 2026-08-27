# Trackly

The domain glossary for Trackly — a single-board kanban tool. This file is the shared
language of the project; use these terms exactly, in code, docs, and conversation.

## Collaboration (board-service)

**Board**:
The single kanban board a team collaborates on. Trackly is intentionally
one-board-per-deployment.
_Avoid_: project, workspace.

**Swimlane**:
An ordered column on the board representing a stage of the workflow (e.g. *To Do*,
*In Progress*, *Done*). A board owns its swimlanes and their order.
_Avoid_: column, list, stage.

**Ticket**:
A unit of work that lives in exactly one swimlane and moves across swimlanes as work
progresses. Has a title, description, and an optional assignee.
_Avoid_: card, task, issue.

**Assignee**:
The user responsible for a ticket. Referenced by user id only — the board context does
not own user data.
_Avoid_: owner, member.

## Notification (notification-service)

**Activity**:
An immutable record describing something that happened on the board (a ticket was
created, moved, or assigned), derived from a board domain event. Displayed as a feed.
_Avoid_: notification, log, event log.

**Recipient**:
The single user an Activity concerns personally — the assignee a ticket gained, or the
assignee of a ticket somebody else moved. Most activities have none, and an Activity is
never addressed to the user who caused it.
_Avoid_: target, subscriber, watcher.

**Activity stream**:
The live channel a signed-in user holds open. It delivers the Activities addressed to them,
as they are recorded — a user only ever receives their own — and the Board changes, which
everybody connected receives.
_Avoid_: feed (that is the Activity list), socket, channel.

**Board change**:
The announcement that somebody changed the board, derived from the same Domain event as an
Activity but addressed to nobody. It says that the board changed and who changed it, never
what it now looks like: a board on screen answers one by asking board-service for the board
again.
_Avoid_: refresh, update, sync.

## Cross-cutting

**Domain event**:
A fact published by board-service after a state change (e.g. `TicketMoved`), delivered
asynchronously via the Service Bus topic. The notification context reacts to these.
_Avoid_: message, signal.

**User**:
An authenticated principal. Owned by identity-service; every other service references a
user by its id (the JWT `sub`) and never stores credentials.
_Avoid_: account, member, principal.

**Admin**:
A user who may also shape the board itself — rename it, and add, reorder or delete its
swimlanes. Carried as the `ROLE_ADMIN` role on the user's token and enforced in
board-service, not merely reflected in the client. Every user, admin or not, may create,
assign and move tickets.
_Avoid_: owner, moderator, superuser.