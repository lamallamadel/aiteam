package com.atlasia.ai.e2e.scenarios;

import com.atlasia.ai.api.dto.*;
import com.atlasia.ai.e2e.configuration.AbstractE2ETest;
import com.atlasia.ai.model.*;
import com.atlasia.ai.persistence.*;
import com.atlasia.ai.service.JwtService;
import com.atlasia.ai.service.MfaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthenticationFlowE2ETest extends AbstractE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private OAuth2AccountRepository oauth2AccountRepository;

    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MfaService mfaService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private UserEntity testUser;
    private String testPassword = "TestPassword123!";
    private String testEmail = "authtest@example.com";

    @BeforeEach
    void setUp() {
        WireMock.reset();
        
        jdbcTemplate.execute("DELETE FROM oauth2_accounts;");
        jdbcTemplate.execute("DELETE FROM password_reset_tokens;");
        jdbcTemplate.execute("DELETE FROM user_roles;");
        jdbcTemplate.execute("DELETE FROM refresh_tokens;");
        jdbcTemplate.execute("DELETE FROM users;");
        jdbcTemplate.execute("DELETE FROM roles;");

        RoleEntity userRole = new RoleEntity("USER", "Standard user");
        roleRepository.save(userRole);

        testUser = new UserEntity("authuser", testEmail, passwordEncoder.encode(testPassword));
        testUser.setEnabled(true);
        testUser.setRoles(Set.of(userRole));
        testUser = userRepository.save(testUser);
    }

    @Test
    @Order(1)
    void testCompleteRegistrationAndLoginFlow() throws Exception {
        String newEmail = "newuser@example.com";
        String newPassword = "NewPassword123!";
        
        UserRegistrationRequest registrationRequest = new UserRegistrationRequest(
                "newuser",
                newEmail,
                newPassword
        );
        
        HttpEntity<UserRegistrationRequest> registrationEntity = new HttpEntity<>(registrationRequest);
        
        ResponseEntity<String> registrationResponse = restTemplate.exchange(
                "/api/auth/register",
                HttpMethod.POST,
                registrationEntity,
                String.class
        );
        
        assertEquals(HttpStatus.CREATED, registrationResponse.getStatusCode(),
                "Registration should succeed. Body: " + registrationResponse.getBody());
        
        JsonNode regJson = objectMapper.readTree(registrationResponse.getBody());
        assertNotNull(regJson.get("userId"), "User ID should be returned");
        assertNotNull(regJson.get("username"), "Username should be returned");
        
        LoginRequest loginRequest = new LoginRequest("newuser", newPassword);
        HttpEntity<LoginRequest> loginEntity = new HttpEntity<>(loginRequest);
        
        ResponseEntity<String> loginResponse = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                loginEntity,
                String.class
        );
        
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode(),
                "Login should succeed. Body: " + loginResponse.getBody());
        
        JsonNode loginJson = objectMapper.readTree(loginResponse.getBody());
        assertNotNull(loginJson.get("accessToken"), "Access token should be present");
        assertNotNull(loginJson.get("refreshToken"), "Refresh token should be present");
        assertTrue(loginJson.get("expiresIn").asLong() > 0, "Token expiration should be positive");
    }

    @Test
    @Order(2)
    void testMfaSetupAndVerificationFlow() throws Exception {
        String accessToken = jwtService.generateAccessToken(testUser);
        
        HttpHeaders setupHeaders = new HttpHeaders();
        setupHeaders.setBearerAuth(accessToken);
        HttpEntity<Void> setupEntity = new HttpEntity<>(setupHeaders);
        
        ResponseEntity<String> setupResponse = restTemplate.exchange(
                "/api/auth/mfa/setup",
                HttpMethod.POST,
                setupEntity,
                String.class
        );
        
        assertEquals(HttpStatus.OK, setupResponse.getStatusCode(),
                "MFA setup should return 200. Body: " + setupResponse.getBody());
        
        JsonNode setupJson = objectMapper.readTree(setupResponse.getBody());
        assertNotNull(setupJson.get("secret"), "Secret should be present");
        assertNotNull(setupJson.get("qrCodeDataUri"), "QR code should be present");
        assertNotNull(setupJson.get("otpAuthUrl"), "OTP Auth URL should be present");
        
        String secret = setupJson.get("secret").asText();
        String qrCode = setupJson.get("qrCodeDataUri").asText();
        assertTrue(qrCode.startsWith("data:image/png;base64,"), "QR code should be base64 PNG");
        
        String validCode = generateValidMfaCode(secret);
        
        MfaVerifySetupRequest verifyRequest = new MfaVerifySetupRequest(secret, validCode);
        HttpEntity<MfaVerifySetupRequest> verifyEntity = new HttpEntity<>(verifyRequest, setupHeaders);
        
        ResponseEntity<String> verifyResponse = restTemplate.exchange(
                "/api/auth/mfa/verify-setup",
                HttpMethod.POST,
                verifyEntity,
                String.class
        );
        
        assertEquals(HttpStatus.OK, verifyResponse.getStatusCode(),
                "MFA verification should succeed. Body: " + verifyResponse.getBody());
        
        JsonNode verifyJson = objectMapper.readTree(verifyResponse.getBody());
        assertTrue(verifyJson.get("mfaEnabled").asBoolean(), "MFA should be enabled");
        
        UserEntity updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertNotNull(updatedUser.getMfaSecret(), "MFA secret should be saved");
        assertTrue(updatedUser.isMfaEnabled(), "MFA should be enabled in database");
    }

    @Test
    @Order(3)
    void testMfaLoginFlowWithValidCode() throws Exception {
        String secret = mfaService.generateSecret();
        testUser.setMfaSecret(secret);
        userRepository.save(testUser);
        
        LoginRequest loginRequest = new LoginRequest(testUser.getUsername(), testPassword);
        HttpEntity<LoginRequest> loginEntity = new HttpEntity<>(loginRequest);
        
        ResponseEntity<String> loginResponse = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                loginEntity,
                String.class
        );
        
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        
        JsonNode loginJson = objectMapper.readTree(loginResponse.getBody());
        assertTrue(loginJson.get("mfaRequired").asBoolean(), "MFA should be required");
        assertNotNull(loginJson.get("mfaToken"), "MFA token should be present");
        
        String mfaToken = loginJson.get("mfaToken").asText();
        String validCode = generateValidMfaCode(secret);
        
        MfaVerifyRequest mfaVerifyRequest = new MfaVerifyRequest(mfaToken, validCode);
        HttpEntity<MfaVerifyRequest> mfaEntity = new HttpEntity<>(mfaVerifyRequest);
        
        ResponseEntity<String> mfaResponse = restTemplate.exchange(
                "/api/auth/mfa/verify",
                HttpMethod.POST,
                mfaEntity,
                String.class
        );
        
        assertEquals(HttpStatus.OK, mfaResponse.getStatusCode(),
                "MFA verification should succeed");
        
        JsonNode mfaJson = objectMapper.readTree(mfaResponse.getBody());
        assertNotNull(mfaJson.get("accessToken"), "Access token should be present");
        assertNotNull(mfaJson.get("refreshToken"), "Refresh token should be present");
    }

    @Test
    @Order(4)
    void testMfaLoginWithInvalidCode() throws Exception {
        String secret = mfaService.generateSecret();
        testUser.setMfaSecret(secret);
        userRepository.save(testUser);
        
        LoginRequest loginRequest = new LoginRequest(testUser.getUsername(), testPassword);
        HttpEntity<LoginRequest> loginEntity = new HttpEntity<>(loginRequest);
        
        ResponseEntity<String> loginResponse = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                loginEntity,
                String.class
        );
        
        JsonNode loginJson = objectMapper.readTree(loginResponse.getBody());
        String mfaToken = loginJson.get("mfaToken").asText();
        
        MfaVerifyRequest mfaVerifyRequest = new MfaVerifyRequest(mfaToken, "000000");
        HttpEntity<MfaVerifyRequest> mfaEntity = new HttpEntity<>(mfaVerifyRequest);
        
        ResponseEntity<String> mfaResponse = restTemplate.exchange(
                "/api/auth/mfa/verify",
                HttpMethod.POST,
                mfaEntity,
                String.class
        );
        
        assertEquals(HttpStatus.UNAUTHORIZED, mfaResponse.getStatusCode(),
                "Invalid MFA code should return 401");
    }

    @Test
    @Order(5)
    void testOAuth2LinkAccountFlow() throws Exception {
        stubFor(get(urlPathEqualTo("/user"))
                .withHeader("Authorization", equalTo("Bearer github-test-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 12345, \"login\": \"testuser\"}")));
        
        String accessToken = jwtService.generateAccessToken(testUser);
        
        OAuth2LinkRequest linkRequest = new OAuth2LinkRequest(
                "github",
                "12345",
                "github-test-token",
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
        
        Optional<OAuth2AccountEntity> linkedAccount = oauth2AccountRepository
                .findByProviderAndProviderUserId("github", "12345");
        assertTrue(linkedAccount.isPresent(), "OAuth2 account should be saved");
        assertEquals(testUser.getId(), linkedAccount.get().getUser().getId());
    }

    @Test
    @Order(6)
    void testOAuth2CallbackWithTokenValidation() throws Exception {
        stubFor(post(urlPathEqualTo("/oauth/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\": \"valid-access-token\", " +
                                "\"refresh_token\": \"valid-refresh-token\", " +
                                "\"expires_in\": 3600}")));
        
        stubFor(get(urlPathEqualTo("/user"))
                .withHeader("Authorization", equalTo("Bearer valid-access-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 67890, \"login\": \"oauth2user\", \"email\": \"oauth2@example.com\"}")));
        
        String accessToken = jwtService.generateAccessToken(testUser);
        
        OAuth2LinkRequest linkRequest = new OAuth2LinkRequest(
                "github",
                "67890",
                "valid-access-token",
                "valid-refresh-token"
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
        
        verify(1, getRequestedFor(urlPathEqualTo("/user"))
                .withHeader("Authorization", equalTo("Bearer valid-access-token")));
    }

    @Test
    @Order(7)
    void testPasswordResetCompleteFlow() throws Exception {
        PasswordResetInitiateRequest initiateRequest = new PasswordResetInitiateRequest(testEmail);
        HttpEntity<PasswordResetInitiateRequest> initiateEntity = new HttpEntity<>(initiateRequest);
        
        ResponseEntity<String> initiateResponse = restTemplate.exchange(
                "/api/auth/password-reset/initiate",
                HttpMethod.POST,
                initiateEntity,
                String.class
        );
        
        assertEquals(HttpStatus.OK, initiateResponse.getStatusCode(),
                "Password reset initiation should succeed. Body: " + initiateResponse.getBody());
        
        JsonNode initiateJson = objectMapper.readTree(initiateResponse.getBody());
        String resetToken = initiateJson.get("token").asText();
        assertNotNull(resetToken, "Reset token should be returned");
        
        String newPassword = "NewPassword456!";
        PasswordResetCompleteRequest completeRequest = new PasswordResetCompleteRequest(resetToken, newPassword);
        HttpEntity<PasswordResetCompleteRequest> completeEntity = new HttpEntity<>(completeRequest);
        
        ResponseEntity<String> completeResponse = restTemplate.exchange(
                "/api/auth/password-reset/complete",
                HttpMethod.POST,
                completeEntity,
                String.class
        );
        
        assertEquals(HttpStatus.OK, completeResponse.getStatusCode(),
                "Password reset completion should succeed");
        
        LoginRequest loginRequest = new LoginRequest(testUser.getUsername(), newPassword);
        HttpEntity<LoginRequest> loginEntity = new HttpEntity<>(loginRequest);
        
        ResponseEntity<String> loginResponse = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                loginEntity,
                String.class
        );
        
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode(),
                "Login with new password should succeed");
    }

    @Test
    @Order(8)
    void testPasswordResetWithExpiredToken() throws Exception {
        Instant expiredTime = Instant.now().minus(2, ChronoUnit.HOURS);
        String tokenHash = passwordEncoder.encode("expired-token");
        
        PasswordResetTokenEntity expiredToken = new PasswordResetTokenEntity(
                testUser.getId(),
                tokenHash,
                expiredTime
        );
        resetTokenRepository.save(expiredToken);
        
        String newPassword = "NewPassword456!";
        PasswordResetCompleteRequest completeRequest =
                new PasswordResetCompleteRequest("expired-token", newPassword);
        HttpEntity<PasswordResetCompleteRequest> completeEntity = new HttpEntity<>(completeRequest);
        
        ResponseEntity<String> completeResponse = restTemplate.exchange(
                "/api/auth/password-reset/complete",
                HttpMethod.POST,
                completeEntity,
                String.class
        );
        
        assertEquals(HttpStatus.BAD_REQUEST, completeResponse.getStatusCode(),
                "Expired token should return 400");
    }

    @Test
    @Order(9)
    void testTokenRefreshFlow() throws Exception {
        LoginRequest loginRequest = new LoginRequest(testUser.getUsername(), testPassword);
        HttpEntity<LoginRequest> loginEntity = new HttpEntity<>(loginRequest);
        
        ResponseEntity<String> loginResponse = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                loginEntity,
                String.class
        );
        
        JsonNode loginJson = objectMapper.readTree(loginResponse.getBody());
        String refreshToken = loginJson.get("refreshToken").asText();
        
        Thread.sleep(1000);
        
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(refreshToken);
        HttpEntity<RefreshTokenRequest> refreshEntity = new HttpEntity<>(refreshRequest);
        
        ResponseEntity<String> refreshResponse = restTemplate.exchange(
                "/api/auth/refresh",
                HttpMethod.POST,
                refreshEntity,
                String.class
        );
        
        assertEquals(HttpStatus.OK, refreshResponse.getStatusCode(),
                "Token refresh should succeed");
        
        JsonNode refreshJson = objectMapper.readTree(refreshResponse.getBody());
        assertNotNull(refreshJson.get("accessToken"), "New access token should be present");
        assertNotEquals(loginJson.get("accessToken").asText(),
                refreshJson.get("accessToken").asText(),
                "New access token should be different");
    }

    @Test
    @Order(10)
    void testLogoutFlow() throws Exception {
        LoginRequest loginRequest = new LoginRequest(testUser.getUsername(), testPassword);
        HttpEntity<LoginRequest> loginEntity = new HttpEntity<>(loginRequest);
        
        ResponseEntity<String> loginResponse = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                loginEntity,
                String.class
        );
        
        JsonNode loginJson = objectMapper.readTree(loginResponse.getBody());
        String refreshToken = loginJson.get("refreshToken").asText();
        
        RefreshTokenRequest logoutRequest = new RefreshTokenRequest(refreshToken);
        HttpEntity<RefreshTokenRequest> logoutEntity = new HttpEntity<>(logoutRequest);
        
        ResponseEntity<String> logoutResponse = restTemplate.exchange(
                "/api/auth/logout",
                HttpMethod.POST,
                logoutEntity,
                String.class
        );
        
        assertEquals(HttpStatus.OK, logoutResponse.getStatusCode(),
                "Logout should succeed");
        
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(refreshToken);
        HttpEntity<RefreshTokenRequest> refreshEntity = new HttpEntity<>(refreshRequest);
        
        ResponseEntity<String> refreshResponse = restTemplate.exchange(
                "/api/auth/refresh",
                HttpMethod.POST,
                refreshEntity,
                String.class
        );
        
        assertEquals(HttpStatus.UNAUTHORIZED, refreshResponse.getStatusCode(),
                "Refresh with revoked token should fail");
    }

    @Test
    @Order(11)
    void testMultipleOAuth2ProvidersLinking() throws Exception {
        stubFor(get(urlPathEqualTo("/user"))
                .withHeader("Authorization", equalTo("Bearer github-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 11111, \"login\": \"testuser\"}")));
        
        stubFor(get(urlPathEqualTo("/oauth2/v2/userinfo"))
                .withHeader("Authorization", equalTo("Bearer google-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": \"22222\", \"email\": \"test@gmail.com\"}")));
        
        String accessToken = jwtService.generateAccessToken(testUser);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        
        OAuth2LinkRequest githubRequest = new OAuth2LinkRequest("github", "11111", "github-token", null);
        ResponseEntity<String> githubResponse = restTemplate.exchange(
                "/api/auth/oauth2/link",
                HttpMethod.POST,
                new HttpEntity<>(githubRequest, headers),
                String.class
        );
        assertEquals(HttpStatus.OK, githubResponse.getStatusCode());
        
        OAuth2LinkRequest googleRequest = new OAuth2LinkRequest("google", "22222", "google-token", null);
        ResponseEntity<String> googleResponse = restTemplate.exchange(
                "/api/auth/oauth2/link",
                HttpMethod.POST,
                new HttpEntity<>(googleRequest, headers),
                String.class
        );
        assertEquals(HttpStatus.OK, googleResponse.getStatusCode());
        
        assertEquals(2, oauth2AccountRepository.findByUserId(testUser.getId()).size(),
                "User should have 2 linked OAuth2 accounts");
    }

    @Test
    @Order(12)
    void testGetCurrentUserWithAuthentication() throws Exception {
        String accessToken = jwtService.generateAccessToken(testUser);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/me",
                HttpMethod.GET,
                entity,
                String.class
        );
        
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Get current user should succeed");
        
        JsonNode userJson = objectMapper.readTree(response.getBody());
        assertEquals(testUser.getUsername(), userJson.get("username").asText());
        assertEquals(testUser.getEmail(), userJson.get("email").asText());
        assertTrue(userJson.get("enabled").asBoolean());
    }

    private String generateValidMfaCode(String secret) {
        dev.samstevens.totp.code.CodeGenerator codeGenerator = new dev.samstevens.totp.code.DefaultCodeGenerator();
        dev.samstevens.totp.time.TimeProvider timeProvider = new dev.samstevens.totp.time.SystemTimeProvider();
        try {
            return codeGenerator.generate(secret, Math.floorDiv(timeProvider.getTime(), 30));
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate TOTP code", e);
        }
    }
}
