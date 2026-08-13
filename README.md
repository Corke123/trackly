# Trackly

[![CI](https://github.com/Corke123/trackly/actions/workflows/ci.yaml/badge.svg?branch=main)](https://github.com/Corke123/trackly/actions/workflows/ci.yaml)
[![Infrastructure](https://github.com/Corke123/trackly/actions/workflows/infra.yaml/badge.svg?branch=main)](https://github.com/Corke123/trackly/actions/workflows/infra.yaml)
[![Quality gate](https://sonarcloud.io/api/project_badges/measure?project=Corke123_trackly&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=Corke123_trackly)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=Corke123_trackly&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=Corke123_trackly)

A minimal, functional Trello-like **single-board** kanban application, built as a
microservice monorepo. Trackly is the practical showcase for the bachelor thesis
*"Sistemi za upravljanje verzijama koda i podrška za kontinualnu integraciju i isporuku"*
(University of Banja Luka, Faculty of Electrical Engineering).

> The application itself is deliberately thin. Its purpose is to be a realistic vehicle
> for demonstrating **CI/CD practices end-to-end** — monorepo-aware pipelines, automated
> quality/security gates, Infrastructure as Code, and blue-green deployment on Azure.

## What it does

A team collaborates on a single **Board**. The board has ordered **Swimlanes** (workflow
stages); each swimlane holds **Tickets** that users create, assign, and drag between
lanes. Every board change emits a domain event; a separate service consumes those events
and builds an **Activity** feed.

There are two kinds of **User**, told apart by the `roles` claim in their token:

|                                   | Admin (`ROLE_ADMIN`)                        | User (`ROLE_USER`) |
|-----------------------------------|---------------------------------------------|--------------------|
| Rename the board                  | ✅                                          | —                  |
| Add, reorder and delete swimlanes | ✅ (a lane still holding tickets cannot go) | —                  |
| Create, assign and move tickets   | ✅                                          | ✅                 |

The distinction is enforced in board-service with `@PreAuthorize` on the admin-only endpoints, not
merely hidden in the client: the SPA renders the controls a role can actually use, and the service
answers 403 to the rest either way.

Either kind of user is told, while they are on the board, when somebody **assigns them a ticket** or
**moves a ticket assigned to them** — a snackbar in the top-right corner. The board event travels
board-service → Service Bus → notification-service, which addresses it to a single recipient and
pushes it down that user's server-sent activity stream (ADR 0011). You are never notified of your
own doing.

## Architecture at a glance

```
Browser ─► gateway-service  (Spring Cloud Gateway — BFF, oauth2Login + TokenRelay)
              │   one origin: serves the SPA on /**, the API on /api/**
              │
              ├─► identity-service      (Spring Authorization Server — issues JWTs)
              ├─► board-service         (boards / swimlanes / tickets + outbox)
              └─► notification-service  (activity feed + activity stream)

        board ──────► Service Bus topic (board-events) ──────► notification
        notification ──► SSE /api/activity/stream ──► gateway ──► Browser
```

The browser holds nothing but an opaque session cookie; the gateway keeps the tokens and relays them as Bearer tokens
(ADR 0005). Both live on one origin because the gateway serves the SPA itself (ADR 0006).

| Component              | Stack                                       | Azure target                   |
|------------------------|---------------------------------------------|--------------------------------|
| `trackly-client`       | Angular 22 + Material                       | bundled into the gateway image |
| `gateway-service`      | Spring Cloud Gateway (WebFlux)              | Container Apps                 |
| `identity-service`     | Spring Authorization Server                 | Container Apps                 |
| `board-service`        | Spring Boot 4.1 (WebFlux/MVC)               | Container Apps                 |
| `notification-service` | Spring Boot 4.1                             | Container Apps                 |
| Databases              | PostgreSQL Flexible Server (DB per service) | Azure                          |
| Messaging              | Service Bus (Standard, topic)               | Azure                          |
| Secrets                | Key Vault (via managed identity)            | Azure                          |
| Images                 | Azure Container Registry (Basic)            | Azure                          |

See [`docs/adr/`](docs/adr) for the architectural decisions and their rationale, and
[`CONTEXT.md`](CONTEXT.md) for the domain glossary.

## Version matrix

| Java | Spring Boot | Spring Cloud       | Angular | Node |
|------|-------------|--------------------|---------|------|
| 25   | 4.1.0       | 2025.1.2 (Oakwood) | 22.0    | 24   |

## Repository layout

```
trackly/
├── .github/workflows/     # CI/CD pipelines (change-detection matrix + reusable workflows)
├── .github/actions/       # composite actions (setup, docker build/push, blue-green)
├── gateway-service/       # BFF gateway
├── identity-service/      # OAuth2 authorization server
├── board-service/         # core domain
├── notification-service/  # event consumer + activity feed + activity stream
├── trackly-client/        # Angular SPA (src/, e2e/ stubbed journeys, e2e-stack/ full-stack ones)
├── infra/                 # Terraform (modules + environments/{shared,staging,production})
├── docs/adr/              # architecture decision records
└── CONTEXT.md             # domain glossary
```

## Local development

Requires **Java 25**, **Node 24**, and **Docker**.

```bash
docker compose up --build
```

Open <http://localhost:8080>. The gateway serves the SPA and the API on one origin, so the BFF session cookie is
first-party (mirroring production). You'll be redirected to the identity login page — sign in, then approve the consent
screen. Three accounts are seeded, and which one you use decides what the board lets you do:

| Username | Password | Role         |
|----------|----------|--------------|
| `admin`  | `admin`  | `ROLE_ADMIN` |
| `user`   | `user`   | `ROLE_USER`  |
| `demo`   | `demo`   | `ROLE_USER`  |

The board itself is seeded too (*Trackly Board*, with *To Do* / *In Progress* / *Done*), so there is something to work
with on a fresh database.

To work on the front-end with live reload, have the gateway proxy the dev server instead of serving its bundled copy —
the origin, and therefore the session cookie, stays the same either way (ADR 0006):

```bash
TRACKLY_SERVE_SPA=false docker compose up -d gateway-service
cd trackly-client && npm ci && npm start
```

Notes:
- The OIDC issuer is `http://host.docker.internal:9000` so the browser and the containers
  agree on the issuer in tokens. identity-service is published on `9000`.
- `docker compose up` waits for identity to be healthy before starting the gateway (the
  gateway performs OIDC discovery at startup).
- Pass `--build` when the SPA or a service changed: without it Compose reuses whatever
  `trackly/*:local` image it already has, which silently runs stale code.
- The authorization server only redirects back to a **registered** URI, seeded from
  `TRACKLY_REDIRECT_URI`/`TRACKLY_POST_LOGOUT_REDIRECT_URI`. Reaching the gateway on a different origin (`127.0.0.1`
  rather than `localhost`, say) fails the redirect until those are changed to match.

### Running the tests

```bash
# Commit stage — exactly what CI runs first: compile, unit tests, unit coverage gate. No Docker.
# Same two commands in any service directory: board-service, notification-service,
# identity-service, gateway-service.
cd board-service && ./mvnw package

# Full build — adds the Testcontainers integration tests and the merged coverage gate (needs Docker)
cd board-service && ./mvnw verify

# Frontend commit stage — unit tests behind the coverage gate, then the production bundle
cd trackly-client && npm ci && npm run test:ci && npm run build

# Frontend end-to-end journeys (Playwright drives the built SPA against a stubbed gateway API)
cd trackly-client && npx playwright install chromium && npm run e2e

# The same journeys against the whole running stack — needs `docker compose up` first, and writes
# to whichever board it finds, so point it at a throwaway database
cd trackly-client && npm run e2e:stack
```

Use `./mvnw` rather than `mvn`: the wrapper pins the Maven version, so a local build, the CI build and the Docker build
agree.

## Continuous Integration

Every push to `main` and every pull request targeting it runs
[`.github/workflows/ci.yaml`](.github/workflows/ci.yaml). It path-filters the monorepo and fans the changed services
into the reusable [`service-ci.yaml`](.github/workflows/service-ci.yaml) pipeline (ADR 0009), which is staged as a
deployment pipeline (ADR 0010):

| Stage             | Runs                                                                                                          | Gate             | Typical |
|-------------------|---------------------------------------------------------------------------------------------------------------|------------------|---------|
| Commit stage      | `./mvnw package` — compile, unit tests, JaCoCo unit gate (LINE/BRANCH ≥ 0.85)                                 | blocks the merge | ~2 min  |
| Integration stage | `./mvnw verify` — Testcontainers (Postgres 17, Service Bus emulator), merged JaCoCo gate (LINE/BRANCH ≥ 0.90) | blocks the merge | ~6 min  |
| Package           | Docker image build and Trivy scan                                                                             | blocks the merge | ~3 min  |

The commit stage needs no Docker, so a mistake comes back in about two minutes; the slow, infrastructure-heavy
verification runs behind it.

SonarCloud analyses every pull request alongside these stages and gates on an **A** security rating for new code.
Findings that are deliberate design decisions rather than defects are marked reviewed in SonarCloud, with the reasoning
recorded in [ADR 0017](docs/adr/0017-accepted-static-analysis-findings.md) so it lives in the repository rather than only
in a review comment.

`gateway-service` goes through the same three stages as every other service, with one difference: its image bundles the
SPA (ADR 0006), so a change under `trackly-client/**` triggers the gateway's build, and the gateway's image is the only
one built from the repository root rather than its own directory.

`trackly-client` is not a Maven service, so it has a pipeline of its own —
[`client-ci.yaml`](.github/workflows/client-ci.yaml) — staged on the same principle:

| Stage             | Runs                                                                             | Gate             | Typical |
|-------------------|----------------------------------------------------------------------------------|------------------|---------|
| Commit stage      | `npm run test:ci` — Vitest unit tests, coverage gate (≥ 85%), then `ng build`     | blocks the merge | ~2 min  |
| End-to-end stage  | `npm run e2e` — Playwright journeys for both roles against a stubbed gateway API  | blocks the merge | ~3 min  |

The journeys stub the gateway's API rather than starting the stack: what they are testing is the client's own behaviour
(who may do what, drag and drop, optimistic moves rolling back), and the services' behaviour is already gated by their
Testcontainers tests. A full-stack smoke test belongs against a deployed staging environment, so it arrives with
continuous delivery.

**`CI required`** is the single aggregating status check to require in branch protection — the matrix and
reusable-workflow job names change as services are added, that one does not.

Test results, failing-test details, per-class timings and coverage are rendered into each run's job summary; failing
tests are also annotated inline on the pull request. JaCoCo HTML reports, the Surefire/Failsafe XML and the application
jar are attached to the run as artifacts. A failure on
`main` opens (or comments on) an issue labelled `broken-build`.

Every image carries its provenance: `/actuator/info` reports `build.version`, `build.revision` (the commit),
`build.buildNumber` and `build.buildUrl`. The blue-green deploy asserts on it — a new revision only takes traffic once
`/actuator/info` on its own FQDN reports the commit being deployed, which proves the revision is running that code and
not a cached image.

Continuous **delivery** builds on this: see [Deploying to Azure](#deploying-to-azure) below, and ADR 0015 for how the
Terraform and pipeline responsibilities divide.

### Required setup for CI

Repository configuration lives in [`infra/harden-repo.sh`](infra/harden-repo.sh) rather than in a list of
clicks, because a checklist nobody re-runs is indistinguishable from a checklist nobody ran (ADR 0018).
Read what it would do, then apply it:

```bash
./infra/harden-repo.sh --dry-run all
./infra/harden-repo.sh all
```

It is idempotent and each section can be applied on its own — `labels`, `merge-settings`,
`workflow-permissions`, `ruleset`, `dependabot`, `environments`. What it sets:

| Section | Why |
|---|---|
| `ruleset` | A pull request with **0 required approvals** and the **`CI required`** check must pass. Reviews happen after integration; the gate is the checks, not a reviewer (Ch 3.1.6). Also linear history, up-to-date branches, no force-push, no deletion |
| `labels` | `broken-build`, `deployment`, `dependencies`, `ci`, `docker`, `trackly-client` and one per service. `gh issue create --label broken-build` fails outright if the label does not exist, so `notify-broken-mainline` depends on this |
| `merge-settings` | Squash and rebase only — merge commits would be rejected by the linear-history rule after the UI offered them. Auto-merge on, so a green PR lands without a second visit |
| `workflow-permissions` | `GITHUB_TOKEN` read-only by default; jobs request more where they need it (Ch 5.4) |
| `dependabot` | Security alerts and automated security fixes. The version updates in `dependabot.yml` are a separate feature and work without these |
| `environments` | `staging` and `production` deployable from `main` only, and required reviewers on `production` — see [Deploying to Azure](#deploying-to-azure) |

## Deploying to Azure

Infrastructure is Terraform (`infra/`); delivery is GitHub Actions. Everything is automated except a
one-time bootstrap and a per-environment database grant — see [`infra/README.md`](infra/README.md) for
the runbook and the things that will bite you.

### What a push to `main` does

```
CI (unit → integration → image push to ACR)
  └─ staging: blue-green per changed service, revision at 0% → verified → 100%
       └─ acceptance: e2e:stack Playwright journeys against real Azure
            └─ ⏸ manual approval  (required reviewers on the production environment)
                 └─ production: blue-green, previous revision kept warm
```

Only **changed** services are built and deployed (`dorny/paths-filter`); an unchanged service's live
revision already runs the right image. A `trackly-client` change redeploys `gateway-service`, because the
SPA is bundled into that image (ADR 0006).

The staging step deploys `identity-service` before `gateway-service` — the gateway resolves identity's
OIDC discovery document while starting and will not boot without it.

### Topology

Two environments, `staging` and `production`, sharing one data plane (ADR 0012): one container registry,
one PostgreSQL Flexible Server with a database per service per environment, one Service Bus namespace with
a topic per environment. Each environment has its own Container Apps environment, Key Vault, and four
managed identities — one per service.

Every dependency is reached with a managed identity: PostgreSQL via Entra tokens, Service Bus via RBAC,
Key Vault via `DefaultAzureCredential` (ADR 0013). No password or connection string is on any runtime
path, and **none of it changes local development** — each path is a branch whose default is the existing
Compose behaviour.

The gateway is the only application entry point; identity-service is also public, because the browser is
redirected to its own origin for the login form (ADR 0006).

All eight container apps scale to zero and PostgreSQL is stopped nightly, so expect a 20–40 second cold
start on the first request (ADR 0016). Roughly $32/month running, $20/month hibernated.

### Other workflows

| Workflow | Trigger | What it does |
|---|---|---|
| `infra.yaml` | `infra/**` | `terraform plan` on a PR (posted as a comment), apply on `main` |
| `rollback.yaml` | manual | Re-points traffic to the previous revision. Does **not** revert migrations |
| `hibernate.yaml` | nightly + manual | Stops or starts PostgreSQL — the main cost lever |
| `acr-purge.yaml` | weekly | Keeps ACR Basic under its 10 GB allowance |

### Required setup

Run the bootstrap, which prints everything else it needs:

```bash
SUBSCRIPTION_ID=... GITHUB_OWNER=... ./infra/bootstrap.sh
```

It creates the remote-state storage account and the three GitHub federated identities (ADR 0014), then
prints the `gh variable set` commands for the repository and environment variables. Two things it does
not do:

1. **Apply the repository configuration.** Run [`./infra/harden-repo.sh all`](infra/harden-repo.sh) — see
   [Required setup for CI](#required-setup-for-ci). Its `environments` section installs the required
   reviewers on `production`, and **that rule is the Ch 3.2 approval gate**: without it this pipeline
   performs continuous deployment, not continuous delivery.
2. **Run `./infra/grant-db-identities.sh <env>`** after each environment's first apply. Managed-identity
   database principals can only be created from a live `psql` session. Skip it and the apps start, then
   fail Flyway with an authentication error.

One consequence of the approval gate is worth knowing before you rely on it. A job awaiting environment
approval holds its concurrency group, and `deploy-staging`, `deploy-production` and `infra.yaml`'s apply
deliberately share the `azure-container-apps` group so Terraform and blue-green releases cannot race. So
an unapproved production deploy queues the *next* merge's staging deploy, and the merge after that
cancels the queued one. **Approve or reject each production deploy before merging the next pull
request** (ADR 0018).

## Build & verification status

All four services compile against Java 25 / Spring Boot 4.1 and the Angular app builds against Angular 22 /
TypeScript 6. All three Terraform stacks validate with `terraform fmt` clean, and the workflows pass `actionlint`.
The BFF login has been driven end to end against the Compose stack: the gateway serves the SPA, `demo` signs in at
identity-service with PKCE, and `/api/**` reaches the resource servers with a relayed Bearer token while the browser
holds only a session cookie. Steps that require an Azure subscription or the live GitHub repository (the OIDC exchange,
the actual deploys) are documented above and must be run in your environment.

The client's own suites run green: 118 Vitest unit tests over the store, services and components, and 22 Playwright
journeys covering both roles — including swimlane and ticket drag-and-drop, a rejected move rolling back, and a live
notification arriving as a snackbar. The board, notification, gateway and identity services pass `./mvnw verify` with
their coverage gates intact.

The two roles have also been driven against the Compose stack (`npm run e2e:stack`): `admin` signs in with PKCE, renames
the board, adds a swimlane and creates an assigned ticket, all of which survive a reload; `user` is offered none of those
controls, and reaching for `PATCH /api/boards/1` with that session comes back **403** from board-service.