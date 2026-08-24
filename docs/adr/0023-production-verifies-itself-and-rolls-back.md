# 0023 — Production verifies its own release, and rolls back without a human

The blue-green mechanism of ADR 0007 verifies each new revision on its own per-revision FQDN *before* that
revision takes traffic, and only shifts traffic once `/actuator/info` there reports the commit being
deployed. That is a strong pre-shift gate, and it left two gaps.

**Nothing verified the shift itself.** Every check ran against `https://<revision>.<domain>`, never against
the public hostname a user reaches. A traffic-set call that succeeded against the wrong revision name, an
ingress that kept serving the old revision, a gateway image whose SPA layer was missing while its actuator
answered — all of these produce a green deploy.

**Production had nothing behind it.** Staging is followed by the `e2e:stack` acceptance suite; `deploy-production`
was the last job in the pipeline. So production was the *less* verified of the two environments, which is
the wrong way round.

## What runs now

[`verify-release.sh`](../../.github/scripts/verify-release.sh) runs at the end of every deploy, in both
environments, against the gateway's public FQDN, and always asserts two things: `/actuator/health` answers,
and `/` serves the single-page app. The second check exists because the SPA ships as a layer of the gateway
image (ADR 0006), so a gateway that answers its actuator but not its document root is a broken release the
actuator alone calls healthy.

When the gateway is one of the services being released, the script additionally asserts that
`/actuator/info` reports `build.revision` equal to the commit being deployed. That is the check which proves
the traffic shift itself took effect, and it is the reason this ADR exists.

If that verification fails, `rollback.sh` runs automatically — the same script the manual `rollback.yaml`
workflow calls — re-pointing traffic to the previous revision, which blue-green left warm, for the services
this deploy actually touched.

## Both halves are bound to what the release contains

The first version of this check asserted the gateway's revision unconditionally, and rolled back `all` four
apps on failure. Both were written when every push to `main` rebuilt and redeployed everything, and both
break the moment the change detection of ADR 0009 selects a subset.

A push that touches only `board-service` deploys only `board-service`. The gateway keeps serving whatever
commit last built it, so an unconditional revision assertion can never pass — and cannot be satisfied by
also deploying the gateway, because CI skipped the gateway build and no image exists at that commit. Run
32768918036 failed exactly this way.

The rollback then made it worse. Scoped to `all`, it moved traffic on three apps this release never touched,
retiring healthy revisions and walking staging two commits backwards. A rollback undoes *this* release;
anything it touches beyond that is collateral damage, not recovery. The manual `rollback.yaml` still offers
`all`, because a human asking for it means it.

The residual gap is that a backend-only release is proven by provisioning state plus the staging acceptance
suite, and nothing else: internal apps have no per-revision FQDN a runner can reach, so blue-green skips
their HTTP verification (ADR 0007). Production has no acceptance suite behind it, which makes a
backend-only production release the least verified path in the pipeline. Closing that needs the gateway to
report its downstream revisions, and is not attempted here.

## The condition is the step's outcome, not the job's

The rollback step is `if: always() && steps.verify.outcome == 'failure'`, deliberately not `if: failure()`.

A deploy step that failed *before* its traffic shift leaves the previous, working revision serving. Rolling
back from there would retire a revision that is fine and promote one that is older, turning a failed deploy
into an outage. `if: failure()` fires on any earlier failure and would do exactly that; keying on the verify
step's own outcome means the automatic rollback fires only in the one situation it is for — traffic was
shifted, and what it was shifted to does not work. If an earlier step failed, `verify` is `skipped`, not
`failure`, and nothing moves.

## Considered options

- **Canary with metric-based gating** — ADR 0007 already weighed and rejected this; nothing here changes
  that reasoning.
- **Rolling production back from a failed staging acceptance run** — unnecessary. Production is never
  reached: `deploy-production` needs `acceptance-staging`, so a failed acceptance suite stops the pipeline
  rather than requiring a rollback.
- **Re-running the full acceptance suite against production** — the `e2e:stack` journeys write to whatever
  board they find (see the README) and would create tickets in production on every release. A read-only
  smoke test is the right shape for a post-release check; the destructive suite belongs on staging.
- **Leaving rollback manual** — defensible while production had a human at the approval gate anyway, but the
  gate approves the *decision to release*, and the failure this catches happens minutes later. Ch 3.3 lists
  automated rollback as one of the mechanisms that make automated release tolerable, and this is the
  cheapest honest version of it.

## Consequences

- The pipeline now performs an automated rollback, which the thesis can describe as implemented rather than
  as future work. Feature flags and canary releases remain deliberately absent (ADR 0007), and the thesis
  should say so rather than imply a complete Ch 3.3 toolkit.
- Schema migrations are **not** rolled back — `rollback.sh` moves traffic and says so in its summary. A
  backwards-incompatible migration is still a forward-fix situation, and pretending otherwise would be the
  more dangerous claim.
- Both environments get the same post-release check, so staging genuinely rehearses the production release
  path rather than approximating it.
- The check adds roughly 5–15 s to a warm deploy, and up to its 300 s timeout against an app scaled to zero
  (ADR 0016) — the cold start the README already documents as 20–40 s.
- The script's outcomes are **verified**, against a local TLS server standing in for the ingress: a
  healthy revision serving the expected commit exits 0 and writes its summary; a revision serving a
  *different* commit, a revision whose actuator answers but whose document root 404s, and a revision that
  never becomes healthy each exit 1 with the intended message. Two more cover the gateway-not-released
  path: a revision whose commit lags the release now exits 0, while a missing document root still exits 1.
  The rollback branch no longer needs a contrived proof — run 32768918036 fired it against staging for real,
  which is also how its scoping bug was found. What remains unexercised is a rollback that recovers a
  genuinely broken release rather than a wrongly-failed verification.

- Writing that test is what caught the bug that would have made all of this moot. The script's first line
  of argument checking read `: "${FQDN:?FQDN must be set to the gateway's public hostname}"`, and the
  apostrophe in *gateway's* opens a single quote inside the parameter expansion that never closes — bash
  reports the failure at the next quote it finds, twenty lines later, in an unrelated `jq` expression. The
  file was a **syntax error**, so the deploy job would have died immediately on both environments. It is
  worth naming because nothing else in the pipeline would have caught it: `actionlint` and `zizmor` read
  workflow YAML and the shell inside `run:` blocks, not the external scripts those blocks call, and
  `shellcheck` is not wired up for `.github/scripts/`. A `bash -n` over the scripts belongs in the `lint`
  job, and that is now the obvious next addition to ADR 0021's checker.
