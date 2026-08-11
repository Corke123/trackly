# 0015 — Terraform owns infrastructure; the deploy pipeline owns revisions

Terraform provisions the platform — registry, database, Service Bus, Key Vault, Container Apps
environments and the four container apps themselves — and then stops. Which **image** each app runs, and
which **revision** receives traffic, belongs to the deploy pipeline, which uses `az containerapp update`
and `az containerapp ingress traffic set` (the blue-green mechanism of ADR 0007).

The seam is a `lifecycle` block on every container app:

```hcl
ignore_changes = [
  template[0].container[0].image,
  template[0].revision_suffix,
  ingress[0].traffic_weight,
  secret,
]
```

Terraform sets these once, on create, and never again. `infra.yaml` runs only on changes under
`infra/**`; a normal commit never invokes Terraform at all.

This is the split ADR 0009 anticipated when it listed a blue-green deploy composite action alongside the
build and setup ones.

## Considered options

- **Terraform owns the image too** — every deploy becomes `terraform apply -var image_tag=sha-…`. The
  purest infrastructure-as-code claim, and a single source of truth. Rejected: expressing blue-green in
  HCL means juggling `revision_suffix` and two `traffic_weight` blocks across two applies, each deploy
  costs a state lock and several minutes, and a failed smoke test leaves state to untangle by hand —
  during a live demo. It also puts every application commit behind the infrastructure state lock.
- **Terraform owns the image, single revision, rolling replace** — simplest possible pipeline and still
  pure IaC, but it abandons ADR 0007 and with it the Ch 3.3 blue-green and rollback demonstration, which
  is the most visible part of the whole exercise. Rejected.

## Consequences

- **A Terraform change to a container app is invisible until traffic is moved.** In
  `revision_mode = "Multiple"`, *any* template change — an environment variable, a probe, a CPU size —
  creates a new revision receiving 0% of traffic, and because `traffic_weight` is ignored, Terraform will
  not move traffic to it. The apply reports success and nothing observable changes. `infra.yaml`
  therefore shifts traffic to `properties.latestRevisionName` after every environment apply. This is the
  least obvious failure mode in the deployment, and it is documented in `infra/README.md` for anyone
  applying by hand.
- `terraform plan` can no longer tell the truth about traffic distribution, so the pipeline's own step
  summary is the record. Each deploy prints a live traffic table read from `az containerapp show`.
- The deploy identity needs no access to Terraform state, and deploys take about a minute.
- The first apply needs a real image to exist, which is why CI publishes a `latest` tag on `main` and the
  container apps default to it. A placeholder such as `mcr.microsoft.com/k8se/quickstart` does not work:
  it listens on port 80 while `target_port` is 8080 or 9000, so the startup probe fails and the apply
  breaks anyway.

## Migrations must be additive

Flyway runs inside the application at startup, so **a new revision migrates the database while the
previous revision is still serving 100% of traffic**, and a rollback moves traffic without reverting the
schema. Both directions therefore require the two adjacent versions to tolerate one schema.

The rule: migrations add, they do not rename or drop. New tables and nullable columns are fine; renaming
a column, dropping one, or narrowing a type in the same release as the code that stops using it is not.
Removal is a second release, after the code that read it is gone everywhere.

This is enforced by the integration checklist in the pull request template, not by tooling.

## What the pipeline verifies before shifting traffic

Externally-ingressed apps (gateway, identity) are probed on their per-revision FQDN: `/actuator/health`,
then `/actuator/info` asserting `build.revision` equals the deployed commit — which proves the revision
is running the code we think it is, not a cached image.

Internally-ingressed apps (board, notification) **cannot** get a pre-traffic HTTP gate. Their
per-revision name is only resolvable inside the Container Apps environment, and at `min_replicas = 0` a
revision with no traffic has no replica to answer. They are gated on `provisioningState`, and their real
verification is the `e2e:stack` acceptance suite that runs against staging immediately afterwards. This is
a genuine gap rather than a complete gate, and it is the cost of scaling to zero (ADR 0016).

A failed gate simply does not shift traffic, so a failed deploy is a no-op for users.
