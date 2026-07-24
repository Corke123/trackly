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

## Cross-cutting

**Domain event**:
A fact published by board-service after a state change (e.g. `TicketMoved`), delivered
asynchronously via the Service Bus topic. The notification context reacts to these.
_Avoid_: message, signal.

**User**:
An authenticated principal. Owned by identity-service; every other service references a
user by its id (the JWT `sub`) and never stores credentials.
_Avoid_: account, member, principal.