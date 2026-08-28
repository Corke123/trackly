# Trackly

[![CI](https://github.com/Corke123/trackly/actions/workflows/ci.yaml/badge.svg?branch=main)](https://github.com/Corke123/trackly/actions/workflows/ci.yaml)
[![Infrastructure](https://github.com/Corke123/trackly/actions/workflows/infra.yaml/badge.svg?branch=main)](https://github.com/Corke123/trackly/actions/workflows/infra.yaml)
[![Quality gate — infrastructure](https://sonarcloud.io/api/project_badges/measure?project=Corke123_trackly&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=Corke123_trackly)
[![Maintainability — infrastructure](https://sonarcloud.io/api/project_badges/measure?project=Corke123_trackly&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=Corke123_trackly)

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
| Comment on a ticket               | ✅                                          | ✅                 |
| Delete a comment                  | ✅ (anybody's)                              | ✅ (their own)     |

The distinction is enforced in board-service with `@PreAuthorize` on the admin-only endpoints, not
merely hidden in the client: the SPA renders the controls a role can actually use, and the service
answers 403 to the rest either way.

Either kind of user may open a ticket to read and add **comments**. A comment is never edited — a correction is
another comment — and it is removed by whoever wrote it, or by an admin.

Either kind of user is told, while they are on the board, when somebody **assigns them a ticket**,
**moves a ticket assigned to them** or **comments on a ticket assigned to them** — a snackbar in the
top-right corner. The board event travels
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
├── trackly-shared/        # parent POM: the build, coverage and style gates every service inherits (ADR 0024, 0025)
├── trackly-client/        # Angular SPA (src/, e2e/ stubbed journeys, e2e-stack/ full-stack ones)
├── infra/                 # Terraform (modules + environments/{shared,staging,production})
├── docs/adr/              # architecture decision records
└── CONTEXT.md             # domain glossary
```

## Local development

Requires **Java 25**, **Node 24**, and **Docker**.

Compose runs the infrastructure; the services run on your machine, so you get a debugger and a restart in seconds
rather than an image rebuild.

```bash
docker compose up -d
```

That starts PostgreSQL, the Azure Service Bus emulator and the SQL Server it needs. Then run each service from its own
directory. identity-service and gateway-service need nothing configured — `application.yaml` already defaults to
`localhost`:

```bash
cd identity-service && ./mvnw spring-boot:run    # :9000, start this one first
cd gateway-service  && ./mvnw spring-boot:run    # :8080
```

board-service and notification-service also need the `local` profile, which selects the emulator's connection string
over the managed identity used in Azure (ADR 0013):

```bash
export SPRING_PROFILES_ACTIVE=local
export SERVICEBUS_CONNECTION_STRING='Endpoint=sb://localhost;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=SAS_KEY_VALUE;UseDevelopmentEmulator=true;'
cd board-service        && ./mvnw spring-boot:run    # :8081
cd notification-service && ./mvnw spring-boot:run    # :8082
```

Both talk to the *same* emulator, which is what makes the board → notification event flow (ADR 0004) work locally.

Finally the front-end. The gateway defaults to proxying the dev server rather than serving a bundled copy, so the
origin — and therefore the session cookie — is the same as production either way (ADR 0006):

```bash
cd trackly-client && npm ci && npm start
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

Notes:
- Start identity-service before gateway-service: the gateway performs OIDC discovery at startup
  and will not boot without it.
- To see the *bundled* SPA instead of the dev server — the mode the image runs — build the bundle
  and point the gateway at it:
  ```bash
  cd trackly-client && npm run build
  cd ../gateway-service && TRACKLY_SERVE_SPA=true \
    TRACKLY_STATIC_LOCATIONS=file:../trackly-client/dist/trackly-client/browser/ ./mvnw spring-boot:run
  ```
- To build a service image locally, build its jar first — the Dockerfile ships the jar rather than
  compiling its own (ADR 0010): `./mvnw package && docker build -t trackly/board-service:local .`
- The authorization server only redirects back to a **registered** URI, seeded from
  `TRACKLY_REDIRECT_URI`/`TRACKLY_POST_LOGOUT_REDIRECT_URI`. Reaching the gateway on a different origin (`127.0.0.1`
  rather than `localhost`, say) fails the redirect until those are changed to match.

### Running the tests

```bash
# Commit stage — exactly what CI runs first: Checkstyle, compile, unit tests, unit coverage gate.
# No Docker. Same two commands in any service directory: board-service, notification-service,
# identity-service, gateway-service.
cd board-service && ./mvnw package

# Style alone, without compiling — the first thing `package` would have failed on
cd board-service && ./mvnw checkstyle:check

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

Each service builds on its own, from its own directory, but inherits its build — compiler settings, the test split, the
coverage gates and the style gate — from `trackly-shared/pom.xml`. Maven reads that parent straight off disk via
`relativePath`, so there is nothing to publish or install first, and a change to the shared build rebuilds all four
services ([ADR 0024](docs/adr/0024-shared-build-in-a-parent-pom.md)).

Java style is one of those inherited gates. Checkstyle runs in the `validate` phase against
`trackly-shared/checkstyle.xml` — Google's ruleset as shipped inside the pinned Checkstyle artifact, with the line
length at 120 and the indentation at what IntelliJ's stock formatter produces here — so `./mvnw package` rejects a
misformatted file before it compiles ([ADR 0025](docs/adr/0025-formatting-is-gated-in-both-languages.md)). The client's
formatting is gated the same way by prettier; `npm run format` fixes it in place, while Checkstyle only reports and
leaves the edit to you.

## Continuous Integration

Every push to `main` and every pull request targeting it runs
[`.github/workflows/ci.yaml`](.github/workflows/ci.yaml). It path-filters the monorepo and fans the changed services
into the reusable [`service-ci.yaml`](.github/workflows/service-ci.yaml) pipeline (ADR 0009), which is staged as a
deployment pipeline (ADR 0010):

| Stage             | Runs                                                                                                          | Gate             | Typical |
|-------------------|---------------------------------------------------------------------------------------------------------------|------------------|---------|
| Commit stage      | `./mvnw package` — Checkstyle, compile, unit tests, JaCoCo unit gate (LINE/BRANCH ≥ 0.85)                     | blocks the merge | ~2 min  |
| Integration stage | `./mvnw verify` — Testcontainers (Postgres 17, Service Bus emulator), merged JaCoCo gate (LINE/BRANCH ≥ 0.90) | blocks the merge | ~6 min  |
| Package           | Layer the commit stage's jar onto the runtime image, then Trivy scan                                          | blocks the merge | ~1 min  |

The commit stage needs no Docker, so a mistake comes back in about two minutes; the slow, infrastructure-heavy
verification runs behind it.

The package stage compiles nothing. It downloads the jar the commit stage built and gated, and the Dockerfile only
splits it into layers and places it on a hardened runtime — so the image that reaches production holds the artifact that
was actually tested, rather than a third independent compile (ADR 0010).

SonarCloud analyses every pull request **inside** these stages rather than beside them. Each module scans itself in the
job that builds it, hands Sonar the coverage that job produced, and waits for the verdict: `sonar.qualitygate.wait`
makes a failing gate fail the job, which fails `CI required`, which the ruleset requires. The repository holds six
Sonar projects — one per service, one for the client, and `Corke123_trackly` for the infrastructure and workflows —
because a project reports the complete state of what it analyses, and the pipeline only builds what changed
([ADR 0019](docs/adr/0019-ci-based-sonar-analysis-per-module.md)).

Pushes to `main` run the same scans without waiting. A branch analysis establishes no new-code period, so SonarCloud
computes no verdict there, and waiting for one fails the build on a gate that never ran.

The gate demands an **A** security rating on new code. Findings that are deliberate design decisions rather than
defects are marked reviewed in SonarCloud, with the reasoning recorded in
[ADR 0017](docs/adr/0017-accepted-static-analysis-findings.md) so it lives in the repository rather than only in a
review comment.

`gateway-service` goes through the same three stages as every other service, with one difference: its image ships the
SPA (ADR 0006), so a change under `trackly-client/**` triggers the gateway's build. The bundle is built once by a
`build-spa` job and shipped as the outermost layer of the gateway image, which is why the gateway is a job of its own
rather than a leg of the service matrix — a matrix leg cannot name a single upstream job in `needs:` (ADR 0009).

`trackly-client` is not a Maven service, so it has a pipeline of its own —
[`client-ci.yaml`](.github/workflows/client-ci.yaml) — staged on the same principle:

| Stage             | Runs                                                                               | Gate             | Typical |
|-------------------|------------------------------------------------------------------------------------|------------------|---------|
| Commit stage      | `npm run format:check`, then `npm run test:ci` — prettier, Vitest, coverage ≥ 85%  | blocks the merge | ~2 min  |
| End-to-end stage  | `npm run e2e` — Playwright journeys for both roles against a stubbed gateway API   | blocks the merge | ~3 min  |

The journeys stub the gateway's API rather than starting the stack: what they are testing is the client's own behaviour
(who may do what, drag and drop, optimistic moves rolling back), and the services' behaviour is already gated by their
Testcontainers tests. A full-stack smoke test belongs against a deployed staging environment, so it arrives with
continuous delivery.

Beside the per-service stages, the `lint` job runs `actionlint`, `zizmor` and a container action that asserts the
pipeline's own wiring invariants — that every service has a style gate and a coverage gate and appears in change
detection, the deploy and Dependabot, and that every job is bounded by a timeout and a permissions block
([ADR 0021](docs/adr/0021-pipeline-invariants-are-checked.md)). Adding a service now fails the pull request until it is
wired in, naming the file that is missing it.

**`CI required`** is the single aggregating status check to require in branch protection — the matrix and
reusable-workflow job names change as services are added, that one does not.

Test results, failing-test details, per-class timings and coverage are rendered into each run's job summary; failing
tests are also annotated inline on the pull request. JaCoCo HTML reports and the Surefire/Failsafe XML are attached to
the run as artifacts; the application jar and the SPA bundle are attached too, and unlike the reports they are load
bearing — the package stage builds the image from them. A failure on
`main` opens (or comments on) an issue labelled `broken-build`.

Every image carries its provenance: `/actuator/info` reports `build.version`, `build.revision` (the commit),
`build.buildNumber` and `build.buildUrl`. The blue-green deploy asserts on it — a new revision only takes traffic once
`/actuator/info` on its own FQDN reports the commit being deployed, which proves the revision is running that code and
not a cached image. After the traffic shift the same assertion is repeated through the environment's *public*
ingress — the only check that can prove the shift itself took effect — together with the document root, because the
SPA ships as a layer of that image. If it fails, traffic is re-pointed to the previous revision **automatically**
([ADR 0023](docs/adr/0023-production-verifies-itself-and-rolls-back.md)).

Each image also ships an inventory of itself: the package stage emits a CycloneDX SBOM and attests it alongside the
build provenance, so what reached production is answerable without pulling the image. Beside these blocking gates,
`codeql.yaml` runs CodeQL over the Java, the TypeScript and — the one most CI/CD writing forgets — the workflows
themselves, on pull requests, on `main`, and weekly, because a query pack updated after a merge finds nothing until
something re-runs ([ADR 0022](docs/adr/0022-supply-chain-scanned-at-every-layer.md)). It is deliberately advisory
rather than blocking; the reasoning is in the ADR.

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
`workflow-permissions`, `ruleset`, `dependabot`, `secret-scanning`, `environments`. What it sets:

| Section | Why |
|---|---|
| `ruleset` | A pull request with **0 required approvals** and the **`CI required`** check must pass. Reviews happen after integration; the gate is the checks, not a reviewer (Ch 3.1.6). Also linear history, up-to-date branches, no force-push, no deletion |
| `labels` | `broken-build`, `deployment`, `dependencies`, `ci`, `docker`, `trackly-client`, `trackly-shared` and one per service. `gh issue create --label broken-build` fails outright if the label does not exist, so `notify-broken-mainline` depends on this |
| `merge-settings` | Squash and rebase only — merge commits would be rejected by the linear-history rule after the UI offered them. Auto-merge on, so a green PR lands without a second visit |
| `workflow-permissions` | `GITHUB_TOKEN` read-only by default; jobs request more where they need it (Ch 5.4) |
| `dependabot` | Security alerts and automated security fixes. The version updates in `dependabot.yml` are a separate feature and work without these |
| `secret-scanning` | Secret scanning and **push protection**, so a committed credential is refused at push time rather than found after it is already in the history (ADR 0022) |
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
| `codeql.yaml` | PR, `main`, weekly | CodeQL over the Java, the TypeScript and the workflows themselves (ADR 0022) |
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
TypeScript 6. All three Terraform stacks validate with `terraform fmt` clean, and the workflows pass `actionlint`
and `zizmor` on every pull request rather than on request.
The BFF login has been driven end to end against the Compose stack: the gateway serves the SPA, `demo` signs in at
identity-service with PKCE, and `/api/**` reaches the resource servers with a relayed Bearer token while the browser
holds only a session cookie. Steps that require an Azure subscription or the live GitHub repository (the OIDC exchange,
the actual deploys) are documented above and must be run in your environment.

The client's own suites run green: 162 Vitest unit tests over the store, services and components, and 31 Playwright
journeys covering both roles — including swimlane and ticket drag-and-drop, a rejected move rolling back, a live
notification arriving as a snackbar, and a comment thread picking up what somebody else wrote. The board, notification, gateway and identity services pass `./mvnw verify` with
their coverage gates intact.

The two roles have also been driven against the Compose stack (`npm run e2e:stack`): `admin` signs in with PKCE, renames
the board, adds a swimlane and creates an assigned ticket, all of which survive a reload; `user` is offered none of those
controls, and reaching for `PATCH /api/boards/1` with that session comes back **403** from board-service. A third
scenario leaves a comment on a ticket, proves it survives a reload, and deletes the ticket to prove the thread goes
with it.