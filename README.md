# Trackly

[![CI](https://github.com/Corke123/trackly/actions/workflows/ci.yaml/badge.svg?branch=main)](https://github.com/Corke123/trackly/actions/workflows/ci.yaml)

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

## Architecture at a glance

```
Browser ─► Azure Static Web Apps (Angular 22 SPA)
              │  reverse-proxies /api, /login, /oauth2
              ▼
        gateway-service  (Spring Cloud Gateway — BFF, oauth2Login + TokenRelay)
         ├─► identity-service      (Spring Authorization Server — issues JWTs)
         ├─► board-service         (boards / swimlanes / tickets + outbox)
         └─► notification-service  (activity feed)
                    ▲
        board ──► Service Bus topic (board-events) ──► notification
```

| Component | Stack | Azure target |
|-----------|-------|--------------|
| `trackly-client` | Angular 22 + Material | Static Web Apps |
| `gateway-service` | Spring Cloud Gateway (WebFlux) | Container Apps |
| `identity-service` | Spring Authorization Server | Container Apps |
| `board-service` | Spring Boot 4.1 (WebFlux/MVC) | Container Apps |
| `notification-service` | Spring Boot 4.1 | Container Apps |
| Databases | PostgreSQL Flexible Server (DB per service) | Azure |
| Messaging | Service Bus (Standard, topic) | Azure |
| Secrets | Key Vault (via managed identity) | Azure |
| Images | Azure Container Registry (Basic) | Azure |

See [`docs/adr/`](docs/adr) for the architectural decisions and their rationale, and
[`CONTEXT.md`](CONTEXT.md) for the domain glossary.

## Version matrix

| Java | Spring Boot | Spring Cloud | Angular | Node |
|------|-------------|--------------|---------|------|
| 25 | 4.1.0 | 2025.1.2 (Oakwood) | 22.0 | 24 |

## Repository layout

```
trackly/
├── .github/workflows/     # CI/CD pipelines (change-detection matrix + reusable workflows)
├── .github/actions/       # composite actions (setup, docker build/push, blue-green)
├── gateway-service/       # BFF gateway
├── identity-service/      # OAuth2 authorization server
├── board-service/         # core domain
├── notification-service/  # event consumer + activity feed
├── trackly-client/        # Angular SPA
├── infra/                 # Terraform (modules + environments/{staging,prod})
├── docs/adr/              # architecture decision records
├── CONTEXT.md             # domain glossary
└── section-6.md           # thesis Section 6 write-up (Serbian)
```

## Local development

Requires **Java 25**, **Node 24**, and **Docker**.

```bash
# 1. Start the backend stack (Postgres + Service Bus emulator + all four services)
docker compose up --build

# 2. In another terminal, run the Angular dev server
cd trackly-client && npm ci && npm start
```

Open <http://localhost:8080>. The gateway serves the SPA and the API on one origin, so the
BFF session cookie is first-party (mirroring production). You'll be redirected to the
identity login page — sign in with the demo credentials **`demo` / `demo`**.

Notes:
- The OIDC issuer is `http://host.docker.internal:9000` so the browser and the containers
  agree on the issuer in tokens. identity-service is published on `9000`.
- `docker compose up` waits for identity to be healthy before starting the gateway (the
  gateway performs OIDC discovery at startup).

### Running the tests

```bash
# Commit stage — exactly what CI runs first: compile, unit tests, unit coverage gate. No Docker.
cd board-service && ./mvnw package

# Full build — adds the Testcontainers integration tests and the merged coverage gate (needs Docker)
cd board-service && ./mvnw verify

# Frontend
cd trackly-client && npm run lint && npm run build
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
| Package           | Docker image build, Trivy scan, smoke test against a real Postgres                                            | blocks the merge | ~3 min  |

The commit stage needs no Docker, so a mistake comes back in about two minutes; the slow, infrastructure-heavy
verification runs behind it.

**`CI required`** is the single aggregating status check to require in branch protection — the matrix and
reusable-workflow job names change as services are added, that one does not.

Test results, failing-test details, per-class timings and coverage are rendered into each run's job summary; failing
tests are also annotated inline on the pull request. JaCoCo HTML reports, the Surefire/Failsafe XML and the application
jar are attached to the run as artifacts. A failure on
`main` opens (or comments on) an issue labelled `broken-build`.

Every image carries its provenance: `/actuator/info` reports `build.version`, `build.revision` (the commit),
`build.buildNumber` and `build.buildUrl`, and the package stage asserts that the running container reports the commit
that built it.

Continuous **delivery** is not wired up yet — see ADR 0010 for the seams it plugs into.

### Required setup for CI

These cannot be committed and must be set once in the GitHub UI:

1. **Ruleset on `main`:** require a pull request with **0 required approvals** (reviews happen after integration; the
   gate is the checks, not a reviewer), require the **`CI required`** status check, require branches to be up to date,
   require linear history, and block force pushes and deletions.
2. **Settings → Actions → General → Workflow permissions:** read-only. Jobs request more where they need it.
3. **Create the labels** the workflows use: `broken-build`, `dependencies`, `ci`, `docker`,
   `board-service`. `gh issue create --label broken-build` fails if the label does not exist.
4. **Settings → Advanced Security:** enable Dependabot alerts and security updates.

## Deploying to Azure

Infrastructure is Terraform (`infra/`); application delivery is GitHub Actions. High level:

1. **Bootstrap** (once, by an admin): create the remote-state storage account, then
   `terraform apply` `infra/environments/shared` and each environment with `-var bootstrap=true`.
   This creates the registry, the platform, and the **GitHub OIDC identities** (ADR 0008).
2. Wire the GitHub repository/environments (below) with the OIDC identity ids output by
   Terraform.
3. Push to `main`: the pipeline builds images, deploys to **staging**, then to
   **production** after a manual approval (Ch 3.2), using blue-green releases (Ch 3.3).
4. One-time: link the gateway as the Static Web App backend so `/api`, `/login`, `/oauth2`
   are reverse-proxied to it (ADR 0006):
   ```bash
   az staticwebapp backends link --name <swa-name> --resource-group <rg> \
     --backend-resource-id <gateway-container-app-id> --backend-region <region>
   ```

### Required GitHub configuration

Repository-level **variables**: `ACR_NAME`, `ACR_LOGIN_SERVER`, `TFSTATE_RESOURCE_GROUP`,
`TFSTATE_STORAGE_ACCOUNT`.

Per **Environment** (`staging`, `production`) **variables**: `AZURE_CLIENT_ID`,
`AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, `RESOURCE_GROUP`. Per-environment **secret**:
`SWA_API_TOKEN` (Static Web Apps deployment token). The `production` environment must have a
**required-reviewers** protection rule to realise the approval gate.

`main` should be a **protected branch** requiring the CI checks to pass (Ch 3.1.6).

## Build & verification status

All four services compile against Java 25 / Spring Boot 4.1 and the Angular app builds
against Angular 22 / TypeScript 6. Terraform validates. Steps that require an Azure
subscription or the live GitHub repository (the OIDC dance, the actual deploys, the SWA
backend link) are documented above and must be run in your environment.