# 0017 — Four static-analysis findings are accepted, not fixed

SonarCloud analyses this repository on every pull request, and its quality gate requires an **A** security
rating on new code — which means zero open vulnerabilities. The Azure deployment introduced eight. Four
were real and were fixed. The other four are accepted and marked **Safe / Won't fix** in SonarCloud, with
the reasoning recorded here rather than only in a review comment that nobody reads again.

All four are rules whose message is a question — *"make sure this is safe here"* — rather than an
assertion that something is broken. Answering that question is the point of the rule, and the answer for
each is below.

## What was fixed first

Worth stating, because it is the argument for having the scanner at all: three of the eight were genuine
supply-chain weaknesses in the pipeline, in code written by hand and reviewed before Sonar saw it.

- **`githubactions:S6505`, twice** — the acceptance job ran `npm ci` without `--ignore-scripts` and then
  `npx playwright install`. Dependency lifecycle scripts executed during a job holding an Azure
  federated-credential token, and `npx` can fetch a package on demand at run time.
- **`githubactions:S8543`** — the same `npx` invocation pinned no version.

Both now use `npm ci --ignore-scripts` and the already-installed `./node_modules/.bin/playwright`, so
nothing executes on install and nothing is fetched mid-pipeline.

- **`terraform:S6381`** — the deploy identity held `Contributor` on its environment's resource group. It
  now holds **`Container Apps Contributor`**, checked against the live role definition to confirm it still
  grants `containerApps/*/read|write|delete|action` and `managedEnvironments/read` — everything
  `az containerapp update`, the traffic re-point and the `defaultDomain` lookup need, and nothing else.
  This was a real least-privilege improvement that the scanner prompted.

## The four that are accepted

### `terraform:S6329` — the container registry allows public network access

**Blocker, and the one that fails the gate.** Disabling it is not possible at this tier and would not help
if it were:

- Private endpoints and IP network rules on Azure Container Registry are **Premium** features. At Basic,
  `public_network_access_enabled = false` has no accompanying private route, so the registry becomes
  unreachable by everything.
- The images are pushed by a GitHub-hosted runner, which is on the public internet.
- The Container Apps environments are Consumption-only with no VNet (ADR 0016), so there is nothing for a
  private endpoint to attach to.

Making this finding genuinely go away means Premium ACR — roughly ten times Basic — plus a
VNet-integrated workload-profile environment and a private DNS zone. That exceeds the entire monthly
budget for this deployment (ADR 0012), and replacing the Container Apps environment would change every
URL in the system (ADR 0015).

What limits the exposure instead: the registry is publicly *reachable* but not publicly *readable*.
`admin_enabled = false` removes the username/password path entirely, `anonymous_pull_enabled = false`
rejects unauthenticated pulls, and every pull requires an Entra token from an identity holding `AcrPull` —
which is only the four container apps. `AcrPush` is held only by the CI identity.

Note for anyone revisiting: declaring `public_network_access_enabled = true` explicitly does **not** clear
this rule. It only changes the message from *"Omitting…"* to *"Make sure allowing… is safe here"*. The
argument is stated explicitly anyway, because a security-relevant setting should be visible in the code
rather than inherited from a default.

### `terraform:S6378` — the registry has no `identity` block

The message reads as though managed identities have been switched off. They have not. This rule is about
the **registry resource having an identity of its own**, which is a different thing from the identity that
pulls images.

The pull path is the container app's own user-assigned identity plus an `AcrPull` assignment, and that is
exactly how ADR 0013 requires it to work. A registry's own identity exists for two purposes: encrypting
registry content with a customer-managed key, and ACR Tasks that authenticate outward to other services.
Neither applies — `acr-purge.yaml` runs `acr purge` against the registry itself and needs no outbound
credential.

Adding an `identity` block would satisfy the rule by creating a managed identity that nothing ever uses.
That is worse infrastructure, not safer infrastructure.

### `terraform:S6382` — ingress does not set `client_certificate_mode`

The argument exists in the provider and accepts `ignore`, `accept` or `require`. It configures mutual TLS.

- **gateway-service and identity-service are browser-facing** (ADR 0006). `require` means every client must
  present a valid client certificate; no browser has one, so every user would fail the TLS handshake.
- **board-service and notification-service are internal-only.** mTLS is conceivable there, but their sole
  caller is the gateway, which would need a keystore configured and certificates provisioned and rotated —
  real work protecting paths that are already unreachable from outside the Container Apps environment.

`accept` would likely clear the finding, since it technically enables client certificates, while enforcing
nothing whatsoever. Rejected as gaming the rule.

### `terraform:S8847` — Key Vault purge protection is disabled

Purge protection prevents anyone — including the subscription owner and Microsoft — from permanently
deleting a vault or its contents before the soft-delete window expires. **It cannot be turned off once
enabled**, and it would make this environment impossible to tear down and rebuild: `terraform destroy`
would soft-delete the vault and block its name for the retention period.

This project is rebuilt repeatedly, so that is a trap rather than a safeguard.

The risk the rule guards against is losing an encryption key and with it everything it encrypted. Nothing
in this vault is irreplaceable: the JWT signing certificate is regenerable (a new key invalidates
outstanding tokens and nothing else), the OAuth2 client secret is regenerable, and the break-glass database
password is recoverable from Azure. `soft_delete_retention_days = 7` still provides a recovery window for
accidental deletion.

The documented upgrade path, if this ever holds something that matters, is to enable purge protection for
production only — accepting that the production vault then becomes permanent.

## Considered options

- **Exclude the whole `infra` tree from analysis** via `sonar.exclusions`. One line, and it would turn the
  gate green immediately. Rejected: it would also hide every *future* Terraform finding, including real
  ones, which is precisely the failure mode that makes teams stop trusting their scanner.
- **Disable these four rules in the quality profile.** Narrower than a path exclusion, but still silences
  the rules for code not yet written. A `client_certificate_mode` finding on a *new* internal service
  might deserve a different answer than it got here.
- **Relax the quality gate condition** below an A security rating on new code. Rejected: the gate is
  useful precisely because it is absolute, and the thesis argues in Ch 3.1.3 for automated checks that
  block.
- **Change the infrastructure to satisfy all four** — Premium ACR, mTLS on public ingress, irreversible
  purge protection, an unused registry identity. Each is either unaffordable, user-breaking, or cargo cult.

Marking the four findings individually keeps every rule active for future code and puts the justification
next to the specific line it applies to.

## Consequences

- The quality gate passes only after a human reviews and marks these four in SonarCloud. That is manual
  work on this pull request, and it recurs if the analysis is reset or the project re-imported.
- Marks are per-issue, so **substantially rewriting one of these resources can resurface its finding** and
  require a fresh review. That is the intended behaviour: the justifications above are tied to the current
  design, and a redesign deserves rechecking rather than inheriting an old dismissal.
- Three of the four are tied to decisions recorded elsewhere — Basic-tier ACR and the budget (ADR 0012),
  the Consumption-only environments (ADR 0016), the browser-facing ingress (ADR 0006). If any of those
  change, the corresponding justification here should be re-examined rather than assumed.
- This is a worked example of the limit of automated analysis for Ch 3.1.3: on the same change the scanner
  found three supply-chain problems and one privilege escalation that manual review had missed, and four
  findings that only a human with the deployment's cost and availability constraints in mind could
  resolve. Both halves matter, and a pipeline that silences the second half loses the first.
