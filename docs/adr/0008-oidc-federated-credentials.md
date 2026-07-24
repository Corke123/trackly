# 0008 — GitHub Actions authenticates to Azure via OIDC federated credentials

CI/CD workflows authenticate to Azure (ACR push, Container Apps deploy, Terraform) using
**OpenID Connect workload identity federation**: `azure/login` exchanges a short-lived
GitHub Actions OIDC token for an Azure access token at run time. No client secret is ever
stored in GitHub. Federated credentials are scoped per GitHub Environment (staging / prod)
via the token's subject claim.

## Considered options

- **Service principal client secret stored as a GitHub secret** — one-time setup, but is
  precisely the long-lived-credential anti-pattern the thesis warns against (Ch 5.4,
  SolarWinds / npm supply-chain incidents) and requires rotation. Rejected.

## Consequences

- Requires an Entra ID app registration with federated credentials plus RBAC role
  assignments (provisioned in Terraform).
- Realizes the thesis Ch 5.4 OIDC section and eliminates standing cloud credentials from
  the repository.
