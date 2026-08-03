-- The client is the gateway (ADR 0005), so both URIs are the gateway's origin. V1 seeded a redirect
-- URI that predates the gateway and no post-logout URI at all, which left RP-initiated logout with
-- nowhere to send the browser back to. Setting both from the placeholders makes the seed converge
-- whenever V1 happened to run.
UPDATE oauth2_registered_client
SET redirect_uris             = '${trackly_redirect_uri}',
    post_logout_redirect_uris = '${trackly_post_logout_redirect_uri}'
WHERE client_id = '${trackly_client_id}';
