# 0006 — The gateway serves the SPA (single origin, no Static Web Apps)

The gateway (Spring Cloud Gateway) serves the Angular SPA itself, in **both** environments.
In production the built SPA is bundled into the gateway container image (copied into Spring's
`classpath:/static/`) and served on `/**`; `/api/*`, `/oauth2/authorization/*`, `/login/*`
and `/logout` are gateway routes. The browser therefore talks to a **single origin** (the
gateway domain), so the BFF `SESSION` cookie (ADR 0005) is first-party and there is no CORS.
identity-service remains a directly-reachable origin for its `/oauth2/authorize` endpoint and
login page (a full-page redirect that sets its own cookie on its own origin). The gateway's
registered `redirect-uri` is its own domain.

Locally the same single-origin property is achieved with a live-reload dev server: the
gateway proxies `/**` to `ng serve` (`FRONTEND_URI`) so the front-end keeps hot reload. The
`trackly.gateway.serve-spa` flag selects between the two modes — `false` (default) proxies to
the dev server, `true` serves the bundled static build. Production sets it `true`.

This supersedes the earlier decision to host the SPA on Azure Static Web Apps, which
reverse-proxied `/api` to the gateway via a linked backend — Static Web Apps has been
dropped entirely.

## Considered options

- **Static Web Apps reverse-proxies the gateway** (previous decision) — keeps a CDN edge,
  but needs the SWA **Standard** plan (~$9/mo) for linked backends, diverges from local dev,
  and carries a documented footgun: the auth paths (`/oauth2/authorization/*`, `/login/*`),
  not just `/api/*`, must be proxied or `oauth2Login` breaks.
- **Azure Front Door in front of a static origin + the gateway** — preserves a CDN edge
  while keeping a single origin, but reintroduces the reverse-proxy routing (and its
  footgun) in a new layer and adds infrastructure. Deferred; revisit only if global edge
  latency becomes a real requirement.

## Consequences

- **One deployable, one origin.** No separate hosting SKU, no linked-backend post-provision
  step, no `staticwebapp.config.json`. The gateway container app is the only public entry
  point, and its FQDN is the OAuth2 `redirect-uri` (now wired in Terraform — no manual
  `az staticwebapp backends link`).
- **Faithful to local dev.** The same origin model runs in both places; the SWA-only footgun
  (proxying the auth paths) cannot occur because there is no proxy layer.
- **A frontend change rebuilds the gateway image.** Because the SPA is bundled into the
  gateway, `trackly-client/**` is part of the gateway's change-detection trigger (ADR 0009),
  and the SPA ships via the same blue-green release as the gateway (ADR 0007).
- **No CDN edge.** Static assets are served from the gateway's Container Apps region rather
  than a POP near the user. Acceptable at this scale; the Front Door option above is the
  documented upgrade path.
