# Infrastructure

Terraform for the Azure deployment. Application delivery is GitHub Actions — see
[ADR 0015](../docs/adr/0015-terraform-owns-infrastructure-cli-owns-revisions.md) for why the running
image is deliberately *not* Terraform's concern.

## Layout

```
infra/
├── bootstrap.sh              # manual, once. Remote state + GitHub federated identities.
├── grant-db-identities.sh    # manual, once per environment. REQUIRED — see below.
├── modules/
│   ├── container-app/        # one service. Holds the lifecycle{} block, hence the module.
│   └── environment/          # everything for one deployable environment.
└── environments/
    ├── shared/               # ACR, PostgreSQL server, Service Bus namespace, Log Analytics, budget
    ├── staging/
    └── production/
```

Three stacks, three state files. `staging` and `production` both read `shared`'s outputs through
`terraform_remote_state`.

## First run, in order

The order matters, and each step exists for a reason.

```bash
SUBSCRIPTION_ID=... GITHUB_OWNER=... ./infra/bootstrap.sh
```

Then follow the three manual steps it prints (GitHub variables, the production required-reviewers rule,
and the shared apply), and:

```bash
gh variable set ACR_NAME --body "$(terraform -chdir=infra/environments/shared output -raw acr_name)"
gh variable set ACR_LOGIN_SERVER --body "$(terraform -chdir=infra/environments/shared output -raw acr_login_server)"
```

**Now run CI once with `force-all: true`** before applying either environment. A container app whose
image tag does not exist in the registry fails to provision, and the apply errors out several minutes
later. The apps default to `:latest`, which CI publishes on `main`.

```bash
terraform -chdir=infra/environments/staging apply -var subscription_id=... -var tfstate_resource_group=... -var tfstate_storage_account=...
./infra/grant-db-identities.sh staging
```

Then the same two commands for `production`.

**Never apply staging and production at the same time.** Both create children of the same PostgreSQL
server and Service Bus namespace, and ARM returns a conflict on the shared parent.

**Apply the environment stacks before merging the commit that introduces them.** `infra.yaml` and
`ci.yaml` both trigger on a push to `main`, and they start together. A shared `azure-container-apps`
concurrency group stops them mutating a container app simultaneously, but it does not order them — so on
the merge that first creates an environment, the deploy can win the lock and fail with
`The containerapp '<name>' does not exist` while Terraform is still building. Applying first avoids it;
otherwise just re-run the CI workflow once the infrastructure run finishes.

## Things that will bite you

### `grant-db-identities.sh` is not optional

Applications authenticate to PostgreSQL with Entra tokens (ADR 0013), and the database principals for
those identities can only be created from a live `psql` session. Skip this and every app starts, then
fails Flyway with an authentication error that does not point back here.

If it goes wrong and you need the environment up *now*, the unblock is to fall back to password
authentication: drop the `authenticationPluginClassName` parameter from the JDBC URL in
`modules/environment/main.tf`, and set `<SVC>_DB_PASSWORD` from the `postgres-admin-password` Key Vault
secret.

### A Terraform apply that changes a container app is invisible until traffic moves

The apps run in `revision_mode = "Multiple"`. **Any** template change — an environment variable, a
probe, a CPU size — creates a *new revision receiving 0% of traffic*, and `ignore_changes` on
`traffic_weight` means Terraform will not move traffic to it. The apply reports success and nothing
observable changes.

`infra.yaml` therefore shifts traffic to `properties.latestRevisionName` for all four apps after any
environment apply. If you apply by hand, do it yourself:

```bash
az containerapp ingress traffic set -n gateway-staging -g rg-trackly-staging --revision-weight "$(az containerapp show -n gateway-staging -g rg-trackly-staging --query properties.latestRevisionName -o tsv)=100"
```

This is the least obvious failure mode in the whole design.

### Replacing the Container Apps environment changes every URL

The public URLs are derived from `azurerm_container_app_environment.default_domain`, which is generated.
Renaming the environment, adding a `workload_profile` block or adding VNet integration all force
replacement — and that changes the OAuth2 issuer, the gateway's registered redirect URI, and any
bookmark. Decide those things *before* the first production apply.

Nothing repairs this automatically. The redirect URI is seeded into `oauth2_registered_client` by Flyway on
first boot, so a changed URL means logging in fails with `invalid_redirect_uri` until you fix the row by
hand:

```bash
psql "host=<server>.postgres.database.azure.com dbname=identity_db_production user=<you> sslmode=require" \
  -c "UPDATE oauth2_registered_client SET redirect_uris='https://<new-gateway>/login/oauth2/code/trackly', post_logout_redirect_uris='https://<new-gateway>' WHERE client_id='trackly';"
```

Tokens already in circulation are not repairable either way.

### The PostgreSQL firewall admits any Azure service

A single `0.0.0.0` rule — what Azure labels "Allow public access from any Azure service" — plus one rule
for the operator's own IP so `grant-db-identities.sh` can connect.

This started out tighter: one rule per environment pinned to
`azurerm_container_app_environment.static_ip_address`. That does not work. On a Consumption-only
environment `static_ip_address` is the **inbound** ingress address; egress leaves through a shared Azure
SNAT pool, and `outboundIpAddresses` on the environment is null. The apps were therefore denied and failed
at startup with `SocketTimeoutException: Connect timed out` rather than anything mentioning the firewall.

What limits the exposure is authentication, not the network: every application connects as its own managed
identity with an Entra token (ADR 0013), those identities exist only in this tenant, and each owns only its
own database. The break-glass administrator password is the one shared credential and no application uses it.

Tightening this properly means VNet-integrating the Container Apps environments so the database can take a
private endpoint — which replaces the environments and therefore changes every URL (see above).

### Costs

Roughly $32/month running, $20/month with PostgreSQL stopped. The nightly `hibernate.yaml` schedule is
the main lever and is worth leaving on. `azurerm_consumption_budget_subscription` emails at 50%, 80%
and a 100% forecast, so a runaway resource arrives as a message rather than a card charge.

Every container app scales to zero, so the Container Apps free grant covers roughly 30 hours per month
of the full eight-app stack. Expect a 20-40 second cold start on the first request after an idle period.
