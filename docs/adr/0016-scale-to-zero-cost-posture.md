# 0016 — Scale to zero and hibernate nightly

Every container app runs with `min_replicas = 0`, and a scheduled workflow stops the shared PostgreSQL
Flexible Server nightly. The deployment is optimised for cost, not availability, because it is a thesis
demonstration on a personal subscription paid out of pocket, and it is only ever used while someone is
looking at it.

Roughly $32/month running, $20/month hibernated. The container apps contribute nothing while idle, and the
Container Apps free grant (180k vCPU-seconds, 360k GiB-seconds per month) covers about 30 hours of the
full eight-app stack — which is why every app is sized `0.5` vCPU / `1Gi` rather than `1.0` / `2Gi`.

A subscription budget notifies at 50%, 80% and a 100% forecast, so a runaway resource arrives as an email
rather than as a card charge.

## Considered options

- **Everything warm** (`min_replicas = 1` on all eight apps) — nothing ever cold-starts, which is the
  safest posture in front of an audience. But eight always-on replicas exhaust the free grant and then
  bill continuously, and it is the single largest avoidable cost in the deployment. Rejected.
- **Staging at zero, production warm** — the middle option, and the one that would remove the cold start
  from the demo itself. One always-on 0.5 vCPU / 1Gi replica is roughly $25–30/month once the grant is
  used. Rejected on cost, but it is a one-variable change if a demo proves unreliable.

## Consequences

Accepted trade-offs, all of which are limitations rather than defects:

- **The first request after an idle period costs 20–40 seconds** of replica activation plus JVM start.
  Container Apps holds the request while this happens, so it appears as a slow page rather than an error.
- **The gateway's session does not survive scaling to zero.** It holds `WebSession` and the OAuth2
  authorized-client store in memory, and Spring Session has no reactive JDBC repository — only Redis and
  MongoDB — so there is no cheap way to externalise it. In practice the loss is invisible:
  identity-service keeps its own SSO session in PostgreSQL (`spring-session-jdbc`) and consent is
  persisted, so the browser is redirected to `/oauth2/authorize` and sent straight back without a
  credential prompt. The one visible failure is narrow: if the gateway sleeps *while the user is sitting
  on the login page*, the pending authorization request is gone and the user sees
  `authorization_request_not_found` and must start again.
- **The gateway's startup depends on identity-service being reachable**, because it resolves the OIDC
  discovery document eagerly during context refresh. With both cold, the gateway's own discovery request
  activates identity; if that request times out, the gateway restarts once and succeeds, since identity is
  warm by then. Self-healing, at the cost of a possible visible restart.
- **Internally-ingressed apps get no pre-traffic health gate**, because a revision with no traffic has no
  replica to probe (ADR 0015).
- **notification-service would stop consuming events entirely while asleep**, which is why the Azure topic
  TTL is `P7D` rather than the emulator's `PT1H` (ADR 0013). An explicit `http_scale_rule` is also
  required, not optional: declaring one is what makes an open SSE stream count as an in-flight request and
  hold the replica.
- **The application is unavailable overnight by design.** A deploy landing after the nightly stop would
  fail when Flyway cannot connect, so every deploy begins by starting the server. Azure auto-starts a
  stopped Flexible Server after seven days, which is why hibernation is a nightly schedule rather than a
  one-off stop.
- `max_replicas = 1` on gateway and notification-service is **not** part of this cost posture — those are
  correctness constraints from in-memory state (ADR 0011). Raising them would break behaviour, not just
  the bill.
