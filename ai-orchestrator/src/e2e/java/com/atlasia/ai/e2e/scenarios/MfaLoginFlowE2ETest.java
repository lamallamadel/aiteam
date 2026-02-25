package com.atlasia.ai.e2e.scenarios;

import com.atlasia.ai.api.dto.LoginRequest;
import com.atlasia.ai.api.dto.MfaVerifyRequest;
import com.atlasia.ai.api.dto.MfaVerifySetupRequest;
import com.atlasia.ai.e2e.configuration.AbstractE2ETest;
import com.atlasia.ai.model.RoleEntity;
import com.atlasia.ai.model.UserEntity;
import com.atlasia.ai.persistence.RoleRepository;
import com.atlasia.ai.persistence.UserRepository;
import com.atlasia.ai.service.JwtService;
import com.atlasia.ai.service.MfaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class MfaLoginFlowE2ETest extends AbstractE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

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
    private String accessToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM user_roles;");
        jdbcTemplate.execute("DELETE FROM refresh_tokens;");
        jdbcTemplate.execute("DELETE FROM users;");
        jdbcTemplate.execute("DELETE FROM roles;");

        RoleEntity userRole = new RoleEntity("USER", "Standard user");
        roleRepository.save(userRole);

        testUser = new UserEntity("mfauser", "mfauser@example.com", passwordEncoder.encode(testPassword));
        testUser.setEnabled(true);
        testUser.setRoles(Set.of(userRole));
        testUser = userRepository.save(testUser);

        accessToken = jwtService.generateAccessToken(testUser);
    }

    @Test
    void testMfaSetupWithQrCode() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> setupResponse = restTemplate.exchange(
                "/api/auth/mfa/setup",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertEquals(HttpStatus.OK, setupResponse.getStatusCode(),
                "MFA setup should return 200 OK. Body: " + setupResponse.getBody());

        JsonNode responseJson = objectMapper.readTree(setupResponse.getBody());
        assertNotNull(responseJson.get("secret"), "Secret should be present");
        assertNotNull(responseJson.get("otpAuthUrl"), "OTP Auth URL should be present");
        assertNotNull(responseJson.get("qrCodeDataUri"), "QR code data URI should be present");

        String secret = responseJson.get("secret").asText();
        assertFalse(secret.isEmpty(), "Secret should not be empty");

        String qrCodeDataUri = responseJson.get("qrCodeDataUri").asText();
        assertTrue(qrCodeDataUri.startsWith("data:image/png;base64,"),
                "QR code should be a base64 PNG image");

        String otpAuthUrl = responseJson.get("otpAuthUrl").asText();
        assertTrue(otpAuthUrl.startsWith("otpauth://totp/"),
                "OTP Auth URL should be valid");
        assertTrue(otpAuthUrl.contains(testUser.getUsername()),
                "OTP Auth URL should contain username");
    }

    @Test
    void testMfaSetupVerificationAndActivation() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> setupEntity = new HttpEntity<>(headers);

        ResponseEntity<String> setupResponse = restTemplate.exchange(
                "/api/auth/mfa/setup",
                HttpMethod.POST,
                setupEntity,
                String.class
        );

        assertEquals(HttpStatus.OK, setupResponse.getStatusCode());

        JsonNode setupJson = objectMapper.readTree(setupResponse.getBody());
        String secret = setupJson.get("secret").asText();

        String validCode = generateValidCode(secret);

        MfaVerifySetupRequest verifyRequest = new MfaVerifySetupRequest(secret, validCode);
        HttpEntity<MfaVerifySetupRequest> verifyEntity = new HttpEntity<>(verifyRequest, headers);

        ResponseEntity<String> verifyResponse = restTemplate.exchange(
                "/api/auth/mfa/verify-setup",
                HttpMethod.POST,
                verifyEntity,
                String.class
        );

        assertEquals(HttpStatus.OK, verifyResponse.getStatusCode(),
                "MFA verification should succeed. Body: " + verifyResponse.getBody());

        JsonNode verifyJson = objectMapper.readTree(verifyResponse.getBody());
        assertEquals("MFA successfully activated", verifyJson.get("message").asText());
        assertTrue(verifyJson.get("mfaEnabled").asBoolean());

        UserEntity updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertNotNull(updatedUser.getMfaSecret(), "MFA secret should be saved");
        assertEquals(secret, updatedUser.getMfaSecret(), "Saved secret should match");
        assertTrue(updatedUser.isMfaEnabled(), "MFA should be enabled");
    }

    @Test
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

        assertEquals(HttpStatus.OK, loginResponse.getStatusCode(),
                "Login should return 200 with MFA required. Body: " + loginResponse.getBody());

        JsonNode loginJson = objectMapper.readTree(loginResponse.getBody());
        assertTrue(loginJson.get("mfaRequired").asBoolean(), "MFA should be required");
        assertNotNull(loginJson.get("mfaToken"), "MFA token should be present");

        String mfaToken = loginJson.get("mfaToken").asText();
        String validCode = generateValidCode(secret);

        MfaVerifyRequest mfaVerifyRequest = new MfaVerifyRequest(mfaToken, validCode);
        HttpEntity<MfaVerifyRequest> mfaEntity = new HttpEntity<>(mfaVerifyRequest);

        ResponseEntity<String> mfaResponse = restTemplate.exchange(
                "/api/auth/mfa/verify",
                HttpMethod.POST,
                mfaEntity,
                String.class
        );

        assertEquals(HttpStatus.OK, mfaResponse.getStatusCode(),
                "MFA verification should succeed. Body: " + mfaResponse.getBody());

        JsonNode mfaJson = objectMapper.readTree(mfaResponse.getBody());
        assertNotNull(mfaJson.get("accessToken"), "Access token should be present");
        assertNotNull(mfaJson.get("refreshToken"), "Refresh token should be present");
        assertTrue(mfaJson.get("expiresIn").asLong() > 0, "Token expiration should be positive");

        String finalAccessToken = mfaJson.get("accessToken").asText();
        assertTrue(jwtService.validateToken(finalAccessToken), "Access token should be valid");
    }

    @Test
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

        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
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

        JsonNode errorJson = objectMapper.readTree(mfaResponse.getBody());
        assertEquals("Invalid code", errorJson.get("error").asText());
    }

    @Test
    void testMfaSetupWithoutAuthentication() {
        HttpEntity<Void> entity = new HttpEntity<>(new HttpHeaders());

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/mfa/setup",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "MFA setup without authentication should return 401");
    }

    @Test
    void testMfaVerifySetupWithInvalidCode() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> setupEntity = new HttpEntity<>(headers);

        ResponseEntity<String> setupResponse = restTemplate.exchange(
                "/api/auth/mfa/setup",
                HttpMethod.POST,
                setupEntity,
                String.class
        );

        assertEquals(HttpStatus.OK, setupResponse.getStatusCode());
        JsonNode setupJson = objectMapper.readTree(setupResponse.getBody());
        String secret = setupJson.get("secret").asText();

        MfaVerifySetupRequest verifyRequest = new MfaVerifySetupRequest(secret, "999999");
        HttpEntity<MfaVerifySetupRequest> verifyEntity = new HttpEntity<>(verifyRequest, headers);

        ResponseEntity<String> verifyResponse = restTemplate.exchange(
                "/api/auth/mfa/verify-setup",
                HttpMethod.POST,
                verifyEntity,
                String.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, verifyResponse.getStatusCode(),
                "Invalid verification code should return 401");

        UserEntity unchangedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertNull(unchangedUser.getMfaSecret(), "MFA secret should not be saved on failed verification");
    }

    private String generateValidCode(String secret) {
        dev.samstevens.totp.code.CodeGenerator codeGenerator = new dev.samstevens.totp.code.DefaultCodeGenerator();
        dev.samstevens.totp.time.TimeProvider timeProvider = new dev.samstevens.totp.time.SystemTimeProvider();
        try {
            return codeGenerator.generate(secret, Math.floorDiv(timeProvider.getTime(), 30));
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate TOTP code", e);
        }
    }
}
