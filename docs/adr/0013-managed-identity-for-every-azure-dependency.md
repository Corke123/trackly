# 0013 — Managed identity for every Azure dependency, with the local path preserved

In Azure, every service authenticates to every dependency as its own user-assigned managed identity:
PostgreSQL via Entra tokens, Service Bus via RBAC, Key Vault via `DefaultAzureCredential`. No password
and no connection string is on any runtime path.

Each is implemented as a **branch whose default is the existing local behaviour**, not as a
replacement. `docker compose up --build` and all four `./mvnw verify` suites are unaffected and need no
Azure account.

| Dependency | Azure | Local | Switch |
|---|---|---|---|
| JWT signing key | Key Vault certificate | committed `dev-signing-key.pem` | `trackly.jwt.signing.source` (already existed) |
| Service Bus | managed identity + RBAC | emulator connection string | the `local` Spring profile |
| PostgreSQL | Entra token via `azure-identity-extensions` | `trackly`/`trackly` password | JDBC URL parameter only — no property |
| OAuth2 client secret | Key Vault secret | Compose literal | existing `TRACKLY_CLIENT_SECRET` |

PostgreSQL needed no Java code at all: pgjdbc loads `AzurePostgresqlAuthenticationPlugin` only when the
JDBC URL names it, so the local URL simply does not name it.

Service Bus is selected by Spring profile rather than by a property. The `local` profile builds the
client from a connection string; with no profile active — which is how it runs in Azure — the client is
built from the namespace and `DefaultAzureCredential`. Compose and the integration tests activate
`local`, so managed identity is the default and the emulator is the special case, rather than the other
way round. Each service therefore keeps a `namespace` and a `connectionString` property and needs no
third property to choose between them.

Three consequences fall out of the choice of *user-assigned* identity:

- `AZURE_CLIENT_ID` is **mandatory** on every service whose code calls `DefaultAzureCredential`. With a
  user-assigned identity and no system-assigned one, IMDS cannot decide which identity to mint a token
  for, and the failure is opaque.
- Role assignments can be made before the app exists, which is what keeps provisioning to a single
  `terraform apply`. A system-assigned identity would not exist until the app did.
- One identity per service, not one per environment — a shared identity would hand the gateway Service
  Bus send rights and hand board-service the JWT signing certificate.

## Two decisions that follow from it

**The registered client stays seeded by Flyway, and the operator supplies the client secret in both
forms.** No application code participates in this.

The redirect URI needs none: Terraform derives the gateway's URL from the Container Apps environment's
`default_domain`, which exists before any container app is created, so `TRACKLY_REDIRECT_URI` is already
correct on identity-service's first boot and `V1`/`V7` seed the right value. There is no chicken-and-egg
to solve.

The client secret does need two forms — identity-service stores a `{bcrypt}` hash, the gateway holds the
plaintext — and Terraform has no `bcrypt()` function. The operator therefore generates the pair once with
`htpasswd -bnBC 10` and stores both as GitHub secrets, and Terraform writes both to Key Vault. Deriving
the hash inside Terraform with `data "external"` was rejected: bcrypt re-salts on every run, so the plan
would show a permanent diff and write a new Key Vault secret version on every apply.

They are **repository** secrets rather than environment secrets, because `infra.yaml` cannot bind a
GitHub environment without changing its OIDC subject away from the one the infra identity accepts
(ADR 0014).

The cost of having no startup reconciliation is that nothing self-heals if the URL changes. Replacing the
Container Apps environment therefore means a manual `UPDATE` on `oauth2_registered_client`, or a new
migration. That is accepted: it happens rarely, and it is preferable to keeping an `ApplicationRunner`
whose only job is to repair configuration.

**The Service Bus topic TTL is `P7D` in Azure, against `PT1H` locally.** With `min_replicas = 0`,
notification-service can be asleep for hours, and a one-hour TTL would silently drop events rather than
queue them. Raising the TTL is what makes a KEDA Service Bus scale rule unnecessary — and that matters,
because `azurerm_container_app`'s `custom_scale_rule` has no `identity` argument, so a scaler would have
forced a SAS connection string back into the system and prevented `local_auth_enabled = false` on the
namespace. The cost is that `recorded_at` lags `occurred_at` while the consumer sleeps, which the
activity schema already distinguishes.

## Considered options

- **Connection strings in Key Vault** — no code change at all, and the secret would at least not be in
  Terraform state. But it leaves a long-lived credential in the system, which is the exact
  anti-pattern ADR 0008 exists to reject; applying that reasoning to CI but not to the application
  would be inconsistent.
- **Connection strings as container-app secrets** — simplest of all, but the value then lives in
  Terraform state in the storage account.
- **Password authentication for PostgreSQL, secret in Key Vault** — much less setup, and no manual step.
  Rejected because the database is where the data is: if managed identity is worth doing anywhere, it is
  worth doing there.

## Consequences

- **Database principals must be created by hand, once per environment.** Terraform cannot call
  `pgaadauth_create_principal` — it needs a live authenticated `psql` session, and the `postgresql`
  provider would need the runner's dynamic IP through the firewall. `infra/grant-db-identities.sh` does
  it. **If it is skipped, every app starts and then fails Flyway with an authentication error that does
  not point back here.** It must be re-run whenever an environment is recreated.
- Each identity is made **owner** of its own database rather than being granted privileges, because
  PostgreSQL 15 and later revoke `CREATE` on the `public` schema from non-owners and Flyway needs it.
- Password authentication stays enabled on the server as a break-glass path, so an administrator
  password does still exist in Key Vault — wired to no application.
- The documented unblock, if Entra authentication fails mid-demo, is to drop the
  `authenticationPluginClassName` parameter and set `<SVC>_DB_PASSWORD` from that secret.
- Nothing about how the application works changed for identity-service: the Key Vault signing path was
  already implemented and merely needed the certificate created in the shape the code expects
  (exportable RSA, self-signed, `application/x-pkcs12`, auto-renewing).
