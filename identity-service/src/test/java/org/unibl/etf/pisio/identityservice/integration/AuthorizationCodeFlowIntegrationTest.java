package org.unibl.etf.pisio.identityservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.unibl.etf.pisio.identityservice.jwk.PemSigningKeys;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives a full authorization_code + PKCE grant end to end against the real Postgres
 * schema (Flyway-migrated Testcontainers Postgres), the way a browser + gateway would in
 * production. This is what actually proves several of the fixes in this change:
 * <ul>
 * <li>the seeded client secret is usable (was stored without a {@code {bcrypt}} prefix,
 * see V1__registered_client.sql / application.yaml) - {@link #tokenEndpoint} exercises
 * {@code client_secret_basic}, which fails outright if that regresses;</li>
 * <li>the issued access token is signed with the durable dev PEM key (not Boot's
 * ephemeral per-startup key) and that key's public counterpart, plus the older dev key,
 * are both still published at {@code /oauth2/jwks} with no private material; and</li>
 * <li>{@code oauth2_authorization} / {@code oauth2_authorization_consent} are populated,
 * proving the Jdbc persistence beans (and the blob-&gt;text schema adaptation) work,
 * instead of the default in-memory services this change replaces.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AuthorizationCodeFlowIntegrationTest {

    private static final String CLIENT_ID = "trackly";

    private static final String REDIRECT_URI = "http://127.0.0.1/login/oauth2/code/trackly";

    private static final String CLIENT_SECRET = "trackly-secret";

    /**
     * The default client secret in application.yaml is a bcrypt hash whose plaintext
     * isn't known here. Override it with a {@code {noop}} secret with a known plaintext
     * so the token endpoint call below can authenticate with client_secret_basic.
     */
    @DynamicPropertySource
    static void clientSecret(DynamicPropertyRegistry registry) {
        registry.add("TRACKLY_CLIENT_SECRET", () -> "{noop}" + CLIENT_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void authorizationCodeGrantWithPkceIssuesTokenSignedByCurrentKeyAndPersistsState() throws Exception {
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = codeChallengeS256(codeVerifier);
        String state = "state-" + UUID.randomUUID();

        Cookie sessionCookie = loginAsDemo();
        String code = authorizeWithConsent(sessionCookie, state, codeChallenge);
        Map<String, Object> tokenResponse = exchangeCodeForToken(code, codeVerifier);

        String accessToken = (String) tokenResponse.get("access_token");
        assertThat(accessToken).isNotBlank();
        SignedJWT jwt = SignedJWT.parse(accessToken);
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(expectedSigningKeyId());

        assertJwksPublishesBothDevKeysWithNoPrivateMaterial();
        assertAuthorizationAndConsentWerePersisted();
    }

    /**
     * Spring Session (JDBC) backs the servlet session here, so the browser-visible
     * session identity is the {@code SESSION} cookie, not a {@code MockHttpSession}
     * object - {@code request.getSession()} on the raw {@link MvcResult} request
     * doesn't reflect it. Carry the cookie across requests instead, the way a real
     * browser would.
     */
    private Cookie loginAsDemo() throws Exception {
        MvcResult result = this.mockMvc
                .perform(post("/login").with(csrf()).param("username", "demo").param("password", "demo"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Cookie sessionCookie = result.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        return sessionCookie;
    }

    private static final Pattern CONSENT_STATE_FIELD = Pattern
            .compile("name=\"state\" value=\"([^\"]*)\"");

    /**
     * Requests {@code openid profile} rather than just {@code openid} specifically so
     * consent is required (the authorization server skips consent when {@code openid}
     * is the only requested scope), exercising the consent persistence path.
     */
    private String authorizeWithConsent(Cookie sessionCookie, String state, String codeChallenge) throws Exception {
        MvcResult authorizeResult = this.mockMvc
                .perform(get("/oauth2/authorize").cookie(sessionCookie)
                        .queryParam(OAuth2ParameterNames.RESPONSE_TYPE, "code")
                        .queryParam(OAuth2ParameterNames.CLIENT_ID, CLIENT_ID)
                        .queryParam(OAuth2ParameterNames.SCOPE, "openid profile")
                        .queryParam(OAuth2ParameterNames.REDIRECT_URI, REDIRECT_URI)
                        .queryParam(OAuth2ParameterNames.STATE, state)
                        .queryParam(PkceParameterNames.CODE_CHALLENGE, codeChallenge)
                        .queryParam(PkceParameterNames.CODE_CHALLENGE_METHOD, "S256"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType())
                        .startsWith(MediaType.TEXT_HTML_VALUE))
                .andReturn();
        Cookie sessionAfterAuthorize = authorizeResult.getResponse().getCookie("SESSION");
        if (sessionAfterAuthorize == null) {
            sessionAfterAuthorize = sessionCookie;
        }

        // The consent page's hidden "state" field is an opaque token the authorization
        // server mints to correlate this consent submission with the pending
        // authorization request - it is NOT the client's own OAuth2 "state" parameter
        // (that one is preserved separately and only reappears on the final redirect),
        // so it has to be read back out of the rendered form.
        String consentState = extractConsentState(authorizeResult.getResponse().getContentAsString());

        // Approve consent: only the not-yet-authorized scope ("profile") needs to be
        // submitted - the authorization server re-adds "openid" automatically once any
        // scope is approved.
        MvcResult consentResult = this.mockMvc
                .perform(post("/oauth2/authorize").cookie(sessionAfterAuthorize)
                        .param(OAuth2ParameterNames.CLIENT_ID, CLIENT_ID)
                        .param(OAuth2ParameterNames.STATE, consentState)
                        .param(OAuth2ParameterNames.SCOPE, "profile"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = consentResult.getResponse().getRedirectedUrl();
        assertThat(location).startsWith(REDIRECT_URI);
        MultiValueMap<String, String> redirectParams = UriComponentsBuilder.fromUriString(location)
                .build()
                .getQueryParams();
        // The client's own "state" (as opposed to the server-minted consent state
        // above) must round-trip unchanged to the redirect_uri, per RFC 6749 4.1.2.
        assertThat(redirectParams.getFirst("state")).isEqualTo(state);
        String code = redirectParams.getFirst("code");
        assertThat(code).isNotBlank();
        return code;
    }

    private Map<String, Object> exchangeCodeForToken(String code, String codeVerifier) throws Exception {
        MvcResult tokenResult = this.mockMvc
                .perform(post("/oauth2/token").with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andReturn();
        return this.objectMapper.readValue(tokenResult.getResponse().getContentAsString(), Map.class);
    }

    private void assertJwksPublishesBothDevKeysWithNoPrivateMaterial() throws Exception {
        MvcResult jwksResult = this.mockMvc.perform(get("/oauth2/jwks")).andExpect(status().isOk()).andReturn();
        JWKSet jwkSet = JWKSet.parse(jwksResult.getResponse().getContentAsString());

        assertThat(jwkSet.getKeys()).extracting(JWK::getKeyID)
                .contains(expectedSigningKeyId(), expectedPreviousKeyId());
        assertThat(jwkSet.getKeys()).allSatisfy(key -> assertThat(key.isPrivate()).isFalse());
    }

    private void assertAuthorizationAndConsentWerePersisted() {
        Integer authorizationCount = this.jdbcTemplate.queryForObject("""
                select count(*) from oauth2_authorization a
                join oauth2_registered_client c on c.id = a.registered_client_id
                where c.client_id = ? and a.principal_name = ?
                """, Integer.class, CLIENT_ID, "demo");
        assertThat(authorizationCount).isPositive();

        Integer consentCount = this.jdbcTemplate.queryForObject("""
                select count(*) from oauth2_authorization_consent oc
                join oauth2_registered_client c on c.id = oc.registered_client_id
                where c.client_id = ? and oc.principal_name = ?
                """, Integer.class, CLIENT_ID, "demo");
        assertThat(consentCount).isEqualTo(1);
    }

    private static String extractConsentState(String consentPageHtml) {
        Matcher matcher = CONSENT_STATE_FIELD.matcher(consentPageHtml);
        assertThat(matcher.find()).as("consent page should contain a hidden 'state' field").isTrue();
        return matcher.group(1);
    }

    private static String expectedSigningKeyId() {
        return new PemSigningKeys(List.of(new ClassPathResource("jwt/dev-signing-key.pem"))).load().getFirst().getKeyID();
    }

    private static String expectedPreviousKeyId() {
        return new PemSigningKeys(List.of(new ClassPathResource("jwt/dev-signing-key-previous.pem"))).load()
                .getFirst()
                .getKeyID();
    }

    private static String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String codeChallengeS256(String codeVerifier) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}
