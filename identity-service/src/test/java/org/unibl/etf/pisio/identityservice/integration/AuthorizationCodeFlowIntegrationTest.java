package org.unibl.etf.pisio.identityservice.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
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

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AuthorizationCodeFlowIntegrationTest {

    private static final String CLIENT_ID = "trackly";

    private static final String REDIRECT_URI = "http://localhost:8080/login/oauth2/code/trackly";

    private static final String CLIENT_SECRET = "trackly-secret";

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    private static final TypeReference<List<Map<String, Object>>> JSON_OBJECTS = new TypeReference<>() {
    };


    @DynamicPropertySource
    static void registeredClient(DynamicPropertyRegistry registry) {
        registry.add("TRACKLY_CLIENT_SECRET", () -> "{noop}" + CLIENT_SECRET);
        registry.add("TRACKLY_REDIRECT_URI", () -> REDIRECT_URI);
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
        assertUserInfoIsReadableWithTheAccessToken(accessToken);
        assertIdTokenCarriesTheRolesTheGatewayNeeds((String) tokenResponse.get("id_token"));
        assertUserDirectoryIsReadableWithTheAccessToken(accessToken);
    }

    private void assertIdTokenCarriesTheRolesTheGatewayNeeds(String idToken) throws Exception {
        assertThat(idToken).isNotBlank();

        JWTClaimsSet claims = SignedJWT.parse(idToken).getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo("demo");
        assertThat(claims.getStringClaim("preferred_username")).isEqualTo("demo");
        assertThat(claims.getStringListClaim("roles")).containsExactly("ROLE_USER");
    }

    private void assertUserDirectoryIsReadableWithTheAccessToken(String accessToken) throws Exception {
        MvcResult usersResult = mockMvc
                .perform(get("/users").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> users = objectMapper.readValue(usersResult.getResponse().getContentAsString(),
                JSON_OBJECTS);
        assertThat(users).extracting(user -> user.get("username")).contains("admin", "demo", "user");

        mockMvc.perform(get("/users")).andExpect(status().isUnauthorized());
    }

    private void assertUserInfoIsReadableWithTheAccessToken(String accessToken) throws Exception {
        MvcResult userInfoResult = mockMvc
                .perform(get("/userinfo").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> claims = objectMapper.readValue(userInfoResult.getResponse().getContentAsString(),
                JSON_OBJECT);
        assertThat(claims).containsEntry("sub", "demo");
    }

    private Cookie loginAsDemo() throws Exception {
        MvcResult result = mockMvc
                .perform(post("/login").with(csrf()).param("username", "demo").param("password", "demo"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Cookie sessionCookie = result.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        return sessionCookie;
    }

    private static final Pattern CONSENT_STATE_FIELD = Pattern
            .compile("name=\"state\" value=\"([^\"]*)\"");

    private String authorizeWithConsent(Cookie sessionCookie, String state, String codeChallenge) throws Exception {
        MvcResult authorizeResult = mockMvc
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

        String consentState = extractConsentState(authorizeResult.getResponse().getContentAsString());

        MvcResult consentResult = mockMvc
                .perform(post("/oauth2/authorize").cookie(sessionAfterAuthorize)
                        .param(OAuth2ParameterNames.CLIENT_ID, CLIENT_ID)
                        .param(OAuth2ParameterNames.STATE, consentState)
                        .param(OAuth2ParameterNames.SCOPE, "profile"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = consentResult.getResponse().getRedirectedUrl();
        assertThat(location).startsWith(REDIRECT_URI);
        MultiValueMap<String, String> redirectParams = UriComponentsBuilder.fromUriString(requireNonNull(location))
                .build()
                .getQueryParams();
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
        return objectMapper.readValue(tokenResult.getResponse().getContentAsString(), JSON_OBJECT);
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
