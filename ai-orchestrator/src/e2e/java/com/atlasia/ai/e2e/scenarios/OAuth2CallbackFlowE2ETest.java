package com.atlasia.ai.e2e.scenarios;

import com.atlasia.ai.api.dto.OAuth2LinkRequest;
import com.atlasia.ai.e2e.configuration.AbstractE2ETest;
import com.atlasia.ai.model.OAuth2AccountEntity;
import com.atlasia.ai.model.RoleEntity;
import com.atlasia.ai.model.UserEntity;
import com.atlasia.ai.persistence.OAuth2AccountRepository;
import com.atlasia.ai.persistence.RoleRepository;
import com.atlasia.ai.persistence.UserRepository;
import com.atlasia.ai.service.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

public class OAuth2CallbackFlowE2ETest extends AbstractE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private OAuth2AccountRepository oauth2AccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private UserEntity testUser;
    private String accessToken;

    @BeforeEach
    void setUp() {
        WireMock.reset();

        jdbcTemplate.execute("DELETE FROM oauth2_accounts;");
        jdbcTemplate.execute("DELETE FROM user_roles;");
        jdbcTemplate.execute("DELETE FROM refresh_tokens;");
        jdbcTemplate.execute("DELETE FROM users;");
        jdbcTemplate.execute("DELETE FROM roles;");

        RoleEntity userRole = new RoleEntity("USER", "Standard user");
        roleRepository.save(userRole);

        testUser = new UserEntity("oauth2user", "oauth2user@example.com",
                passwordEncoder.encode("TestPassword123!"));
        testUser.setEnabled(true);
        testUser.setRoles(Set.of(userRole));
        testUser = userRepository.save(testUser);

        accessToken = jwtService.generateAccessToken(testUser);
    }

    @Test
    void testOAuth2LinkAccountWithGitHub() throws Exception {
        stubFor(get(urlPathEqualTo("/user"))
                .withHeader("Authorization", equalTo("Bearer github-access-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 12345, \"login\": \"testuser\", \"email\": \"test@github.com\"}")));

        OAuth2LinkRequest linkRequest = new OAuth2LinkRequest(
                "github",
                "12345",
                "github-access-token",
                "github-refresh-token"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<OAuth2LinkRequest> entity = new HttpEntity<>(linkRequest, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/oauth2/link",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "OAuth2 link should succeed. Body: " + response.getBody());

        JsonNode responseJson = objectMapper.readTree(response.getBody());
        assertTrue(responseJson.get("linked").asBoolean(), "Account should be linked");
        assertEquals("github", responseJson.get("provider").asText());
        assertEquals("OAuth2 account linked successfully", responseJson.get("message").asText());

        Optional<OAuth2AccountEntity> linkedAccount = oauth2AccountRepository
                .findByProviderAndProviderUserId("github", "12345");
        assertTrue(linkedAccount.isPresent(), "OAuth2 account should be saved in database");
        assertEquals(testUser.getId(), linkedAccount.get().getUser().getId(), "Should be linked to correct user");
    }

    @Test
    void testOAuth2LinkAccountWithGoogle() throws Exception {
        stubFor(get(urlPathEqualTo("/oauth2/v2/userinfo"))
                .withHeader("Authorization", equalTo("Bearer google-access-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": \"67890\", \"email\": \"test@gmail.com\", \"name\": \"Test User\"}")));

        OAuth2LinkRequest linkRequest = new OAuth2LinkRequest(
                "google",
                "67890",
                "google-access-token",
                "google-refresh-token"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<OAuth2LinkRequest> entity = new HttpEntity<>(linkRequest, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/oauth2/link",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "OAuth2 link should succeed. Body: " + response.getBody());

        JsonNode responseJson = objectMapper.readTree(response.getBody());
        assertTrue(responseJson.get("linked").asBoolean());
        assertEquals("google", responseJson.get("provider").asText());

        Optional<OAuth2AccountEntity> linkedAccount = oauth2AccountRepository
                .findByProviderAndProviderUserId("google", "67890");
        assertTrue(linkedAccount.isPresent(), "OAuth2 account should be saved in database");
    }

    @Test
    void testOAuth2LinkWithoutAuthentication() {
        OAuth2LinkRequest linkRequest = new OAuth2LinkRequest(
                "github",
                "12345",
                "github-access-token",
                null
        );

        HttpEntity<OAuth2LinkRequest> entity = new HttpEntity<>(linkRequest);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/oauth2/link",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "OAuth2 link without authentication should return 401");
    }

    @Test
    void testOAuth2LinkDuplicateAccountConflict() throws Exception {
        OAuth2AccountEntity existingAccount = new OAuth2AccountEntity(
                testUser,
                "github",
                "12345",
                "existing-token",
                null
        );
        oauth2AccountRepository.save(existingAccount);

        stubFor(get(urlPathEqualTo("/user"))
                .withHeader("Authorization", equalTo("Bearer github-access-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 12345, \"login\": \"testuser\"}")));

        OAuth2LinkRequest linkRequest = new OAuth2LinkRequest(
                "github",
                "12345",
                "github-access-token",
                null
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<OAuth2LinkRequest> entity = new HttpEntity<>(linkRequest, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/oauth2/link",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode(),
                "Duplicate OAuth2 account should return 409 conflict");

        JsonNode responseJson = objectMapper.readTree(response.getBody());
        assertFalse(responseJson.get("linked").asBoolean());
    }

    @Test
    void testOAuth2LinkWithMultipleProviders() throws Exception {
        stubFor(get(urlPathEqualTo("/user"))
                .withHeader("Authorization", equalTo("Bearer github-access-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 12345, \"login\": \"testuser\"}")));

        stubFor(get(urlPathEqualTo("/oauth2/v2/userinfo"))
                .withHeader("Authorization", equalTo("Bearer google-access-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": \"67890\", \"email\": \"test@gmail.com\"}")));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        OAuth2LinkRequest githubRequest = new OAuth2LinkRequest(
                "github", "12345", "github-access-token", null);
        HttpEntity<OAuth2LinkRequest> githubEntity = new HttpEntity<>(githubRequest, headers);

        ResponseEntity<String> githubResponse = restTemplate.exchange(
                "/api/auth/oauth2/link",
                HttpMethod.POST,
                githubEntity,
                String.class
        );

        assertEquals(HttpStatus.OK, githubResponse.getStatusCode());

        OAuth2LinkRequest googleRequest = new OAuth2LinkRequest(
                "google", "67890", "google-access-token", null);
        HttpEntity<OAuth2LinkRequest> googleEntity = new HttpEntity<>(googleRequest, headers);

        ResponseEntity<String> googleResponse = restTemplate.exchange(
                "/api/auth/oauth2/link",
                HttpMethod.POST,
                googleEntity,
                String.class
        );

        assertEquals(HttpStatus.OK, googleResponse.getStatusCode());

        assertEquals(2, oauth2AccountRepository.findByUserId(testUser.getId()).size(),
                "User should have 2 linked OAuth2 accounts");
    }

    @Test
    void testOAuth2PostMessageEventSimulation() throws Exception {
        stubFor(get(urlPathEqualTo("/user"))
                .withHeader("Authorization", equalTo("Bearer github-access-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 12345, \"login\": \"testuser\"}")));

        OAuth2LinkRequest linkRequest = new OAuth2LinkRequest(
                "github",
                "12345",
                "github-access-token",
                "github-refresh-token"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<OAuth2LinkRequest> entity = new HttpEntity<>(linkRequest, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/oauth2/link",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode responseJson = objectMapper.readTree(response.getBody());
        assertTrue(responseJson.get("linked").asBoolean());

        verify(1, getRequestedFor(urlPathEqualTo("/user"))
                .withHeader("Authorization", equalTo("Bearer github-access-token")));
    }

    @Test
    void testOAuth2LinkWithInvalidProvider() {
        OAuth2LinkRequest linkRequest = new OAuth2LinkRequest(
                "invalid-provider-123!@#",
                "12345",
                "some-token",
                null
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<OAuth2LinkRequest> entity = new HttpEntity<>(linkRequest, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/oauth2/link",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "Invalid provider name should return 400");
    }
}
