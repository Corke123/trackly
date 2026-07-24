# 0005 — Backend-for-Frontend authentication at the gateway

The gateway (Spring Cloud Gateway) is the OAuth2 client: it performs the Authorization
Code + PKCE login (`oauth2Login`) against identity-service (Spring Authorization Server),
holds the tokens **server-side**, and relays the access token to downstream services as a
Bearer token via the `TokenRelay` filter. board-service and notification-service are
OAuth2 **resource servers** that validate the JWT. The browser only ever holds an opaque
`SESSION` cookie — access/refresh tokens never reach JavaScript.

## Considered options

- **SPA as public client** (token in the browser) — simpler and common, but exposes
  tokens to XSS. Rejected for the weaker security story.
- **Gateway opaque-session exchanged for JWT internally** — similar security, more custom
  plumbing than the idiomatic Spring `TokenRelay`.

## Consequences

- The gateway is stateful (holds sessions); acceptable at this scale.
- The BFF session cookie must be first-party — see ADR 0006 (hosting) for how the SPA and
  gateway share an origin in production.
- Directly realizes the thesis Ch 5.4 OIDC / token-handling material.