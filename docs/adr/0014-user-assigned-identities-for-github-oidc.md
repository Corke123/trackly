# 0014 — GitHub's federated credentials sit on user-assigned managed identities

The OIDC workload-identity federation of ADR 0008 is implemented with **user-assigned managed
identities** rather than Entra ID app registrations. The token exchange is identical: `azure/login` still
trades a short-lived GitHub Actions OIDC token for an Azure access token, and no client secret is ever
stored.

This amends ADR 0008, which specified "an Entra ID app registration with federated credentials".

Three identities, created by `infra/bootstrap.sh`:

| Identity | Federated subject | Rights |
|---|---|---|
| `id-trackly-github-infra` | `pull_request` and `ref:refs/heads/main` | Contributor, RBAC Administrator, Key Vault Certificates/Secrets Officer, AcrPush, state storage |
| `id-trackly-github-staging` | `environment:staging` | Contributor + Managed Identity Operator on `rg-trackly-staging` |
| `id-trackly-github-production` | `environment:production` | the same, on `rg-trackly-production` |

## Why not app registrations

Creating an app registration requires the **Application Administrator** directory role, which is a
different and often unavailable permission from subscription Owner. A user-assigned managed identity is
an ordinary Azure resource, so subscription Owner is sufficient — and it is `az identity
federated-credential create` rather than a two-step app-plus-service-principal dance. For a project
deployed on a personal subscription, that removes a whole class of "you do not have permission" failure.

## Consequences

- **A job's `environment:` binding decides which identity it can use**, because the binding changes the
  OIDC subject claim. This is load-bearing in both directions:
  - jobs that must run as the infra identity (`terraform apply`, ACR push, PostgreSQL start/stop) must
    have **no** `environment:`, or their subject becomes `environment:<name>` and the exchange fails;
  - deploy and rollback jobs must have one, or they cannot reach their environment's resources.
- The scoping is genuinely least-privilege: a compromised staging deploy cannot touch production, and
  neither deploy identity can modify infrastructure.
- The per-environment role assignments are made by **Terraform**, not `bootstrap.sh`, because the
  environment resource groups do not exist when bootstrap runs and an RBAC scope must exist first.
- `Managed Identity Operator` on each environment's resource group is not redundant with `Contributor`:
  updating a container app that carries a user-assigned identity re-validates
  `Microsoft.ManagedIdentity/userAssignedIdentities/assign/action`, which `Contributor` does not grant.
  Without it, `az containerapp update` fails with `AuthorizationFailed`.
- `Role Based Access Control Administrator` is required on the infra identity because Terraform creates
  role assignments and `Contributor` cannot. It is narrower than `User Access Administrator`.
- Key Vault **Certificates Officer** and **Secrets Officer** are granted in `bootstrap.sh` at
  subscription scope rather than by Terraform. This is what avoids a two-phase apply: a configuration
  that creates a vault, grants itself data-plane access and then writes to it will 403 on the write.
