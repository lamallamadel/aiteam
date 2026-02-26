package com.atlasia.ai.e2e.scenarios;

import com.atlasia.ai.api.dto.LoginRequest;
import com.atlasia.ai.api.dto.PasswordResetCompleteRequest;
import com.atlasia.ai.api.dto.PasswordResetInitiateRequest;
import com.atlasia.ai.e2e.configuration.AbstractE2ETest;
import com.atlasia.ai.model.PasswordResetTokenEntity;
import com.atlasia.ai.model.RoleEntity;
import com.atlasia.ai.model.UserEntity;
import com.atlasia.ai.persistence.PasswordResetTokenRepository;
import com.atlasia.ai.persistence.RoleRepository;
import com.atlasia.ai.persistence.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PasswordResetFlowE2ETest extends AbstractE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private UserEntity testUser;
    private String testEmail = "resetuser@example.com";
    private String oldPassword = "OldPassword123!";

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM password_reset_tokens;");
        jdbcTemplate.execute("DELETE FROM user_roles;");
        jdbcTemplate.execute("DELETE FROM refresh_tokens;");
        jdbcTemplate.execute("DELETE FROM users;");
        jdbcTemplate.execute("DELETE FROM roles;");

        RoleEntity userRole = new RoleEntity("USER", "Standard user");
        roleRepository.save(userRole);

        testUser = new UserEntity("resetuser", testEmail, passwordEncoder.encode(oldPassword));
        testUser.setEnabled(true);
        testUser.setRoles(Set.of(userRole));
        testUser = userRepository.save(testUser);
    }

    @Test
    void testPasswordResetInitiation() throws Exception {
        PasswordResetInitiateRequest request = new PasswordResetInitiateRequest(testEmail);
        HttpEntity<PasswordResetInitiateRequest> entity = new HttpEntity<>(request);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/password-reset/initiate",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Password reset initiation should succeed. Body: " + response.getBody());

        JsonNode responseJson = objectMapper.readTree(response.getBody());
        assertEquals("Password reset initiated. Check your email for instructions.",
                responseJson.get("message").asText());
        assertNotNull(responseJson.get("token"), "Reset token should be returned");

        String resetToken = responseJson.get("token").asText();
        assertFalse(resetToken.isEmpty(), "Reset token should not be empty");

        List<PasswordResetTokenEntity> tokens = resetTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(testUser.getId()))
                .toList();
        assertEquals(1, tokens.size(), "Should have exactly one reset token");
        assertTrue(tokens.get(0).isValid(), "Reset token should be valid");
    }

    @Test
    void testPasswordResetCompletion() throws Exception {
        PasswordResetInitiateRequest initiateRequest = new PasswordResetInitiateRequest(testEmail);
        HttpEntity<PasswordResetInitiateRequest> initiateEntity = new HttpEntity<>(initiateRequest);

        ResponseEntity<String> initiateResponse = restTemplate.exchange(
                "/api/auth/password-reset/initiate",
                HttpMethod.POST,
                initiateEntity,
                String.class
        );

        assertEquals(HttpStatus.OK, initiateResponse.getStatusCode());
        JsonNode initiateJson = objectMapper.readTree(initiateResponse.getBody());
        String resetToken = initiateJson.get("token").asText();

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
                "Password reset completion should succeed. Body: " + completeResponse.getBody());

        JsonNode completeJson = objectMapper.readTree(completeResponse.getBody());
        assertEquals("Password reset successfully", completeJson.get("message").asText());

        UserEntity updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches(newPassword, updatedUser.getPasswordHash()),
                "Password should be updated to new password");
        assertFalse(passwordEncoder.matches(oldPassword, updatedUser.getPasswordHash()),
                "Old password should no longer work");

        List<PasswordResetTokenEntity> tokens = resetTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(testUser.getId()))
                .toList();
        assertTrue(tokens.stream().allMatch(t -> t.isUsed() || !t.isValid()),
                "All tokens should be used or invalid");
    }

    @Test
    void testLoginWithNewPasswordAfterReset() throws Exception {
        PasswordResetInitiateRequest initiateRequest = new PasswordResetInitiateRequest(testEmail);
        HttpEntity<PasswordResetInitiateRequest> initiateEntity = new HttpEntity<>(initiateRequest);

        ResponseEntity<String> initiateResponse = restTemplate.exchange(
                "/api/auth/password-reset/initiate",
                HttpMethod.POST,
                initiateEntity,
                String.class
        );

        JsonNode initiateJson = objectMapper.readTree(initiateResponse.getBody());
        String resetToken = initiateJson.get("token").asText();

        String newPassword = "NewPassword789!";
        PasswordResetCompleteRequest completeRequest = new PasswordResetCompleteRequest(resetToken, newPassword);
        HttpEntity<PasswordResetCompleteRequest> completeEntity = new HttpEntity<>(completeRequest);

        restTemplate.exchange(
                "/api/auth/password-reset/complete",
                HttpMethod.POST,
                completeEntity,
                String.class
        );

        LoginRequest loginRequest = new LoginRequest(testUser.getUsername(), newPassword);
        HttpEntity<LoginRequest> loginEntity = new HttpEntity<>(loginRequest);

        ResponseEntity<String> loginResponse = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                loginEntity,
                String.class
        );

        assertEquals(HttpStatus.OK, loginResponse.getStatusCode(),
                "Login with new password should succeed. Body: " + loginResponse.getBody());

        JsonNode loginJson = objectMapper.readTree(loginResponse.getBody());
        assertNotNull(loginJson.get("accessToken"), "Should receive access token");
    }

    @Test
    void testPasswordResetWithInvalidToken() {
        String newPassword = "NewPassword456!";
        PasswordResetCompleteRequest completeRequest =
                new PasswordResetCompleteRequest("invalid-token-12345", newPassword);
        HttpEntity<PasswordResetCompleteRequest> completeEntity = new HttpEntity<>(completeRequest);

        ResponseEntity<String> completeResponse = restTemplate.exchange(
                "/api/auth/password-reset/complete",
                HttpMethod.POST,
                completeEntity,
                String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, completeResponse.getStatusCode(),
                "Invalid reset token should return 400");
    }

    @Test
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
                "Expired reset token should return 400");

        UserEntity unchangedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches(oldPassword, unchangedUser.getPasswordHash()),
                "Password should not have changed");
    }

    @Test
    void testPasswordResetWithNonexistentEmail() throws Exception {
        PasswordResetInitiateRequest request = new PasswordResetInitiateRequest("nonexistent@example.com");
        HttpEntity<PasswordResetInitiateRequest> entity = new HttpEntity<>(request);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/password-reset/initiate",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "Password reset with nonexistent email should return 400");

        JsonNode responseJson = objectMapper.readTree(response.getBody());
        assertEquals("Invalid request", responseJson.get("error").asText());
    }

    @Test
    void testPasswordResetTokenValidation() throws Exception {
        PasswordResetInitiateRequest initiateRequest = new PasswordResetInitiateRequest(testEmail);
        HttpEntity<PasswordResetInitiateRequest> initiateEntity = new HttpEntity<>(initiateRequest);

        ResponseEntity<String> initiateResponse = restTemplate.exchange(
                "/api/auth/password-reset/initiate",
                HttpMethod.POST,
                initiateEntity,
                String.class
        );

        JsonNode initiateJson = objectMapper.readTree(initiateResponse.getBody());
        String resetToken = initiateJson.get("token").asText();

        assertFalse(resetToken.isEmpty(), "Token should not be empty");
        assertTrue(resetToken.length() > 20, "Token should be sufficiently long for security");

        List<PasswordResetTokenEntity> tokens = resetTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(testUser.getId()))
                .toList();
        assertEquals(1, tokens.size());
        assertTrue(tokens.get(0).getExpiresAt().isAfter(Instant.now()),
                "Token expiration should be in the future");
    }

    @Test
    void testPasswordResetInvalidatesOldTokens() throws Exception {
        PasswordResetInitiateRequest request1 = new PasswordResetInitiateRequest(testEmail);
        HttpEntity<PasswordResetInitiateRequest> entity1 = new HttpEntity<>(request1);

        ResponseEntity<String> response1 = restTemplate.exchange(
                "/api/auth/password-reset/initiate",
                HttpMethod.POST,
                entity1,
                String.class
        );

        assertEquals(HttpStatus.OK, response1.getStatusCode());
        JsonNode json1 = objectMapper.readTree(response1.getBody());
        String token1 = json1.get("token").asText();

        Thread.sleep(100);

        PasswordResetInitiateRequest request2 = new PasswordResetInitiateRequest(testEmail);
        HttpEntity<PasswordResetInitiateRequest> entity2 = new HttpEntity<>(request2);

        ResponseEntity<String> response2 = restTemplate.exchange(
                "/api/auth/password-reset/initiate",
                HttpMethod.POST,
                entity2,
                String.class
        );

        assertEquals(HttpStatus.OK, response2.getStatusCode());
        JsonNode json2 = objectMapper.readTree(response2.getBody());
        String token2 = json2.get("token").asText();

        assertNotEquals(token1, token2, "New token should be different from old token");

        String newPassword = "NewPassword456!";
        PasswordResetCompleteRequest completeRequest =
                new PasswordResetCompleteRequest(token1, newPassword);
        HttpEntity<PasswordResetCompleteRequest> completeEntity = new HttpEntity<>(completeRequest);

        ResponseEntity<String> completeResponse = restTemplate.exchange(
                "/api/auth/password-reset/complete",
                HttpMethod.POST,
                completeEntity,
                String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, completeResponse.getStatusCode(),
                "Old token should be invalidated when new token is created");
    }

    @Test
    void testPasswordResetWithWeakPassword() throws Exception {
        PasswordResetInitiateRequest initiateRequest = new PasswordResetInitiateRequest(testEmail);
        HttpEntity<PasswordResetInitiateRequest> initiateEntity = new HttpEntity<>(initiateRequest);

        ResponseEntity<String> initiateResponse = restTemplate.exchange(
                "/api/auth/password-reset/initiate",
                HttpMethod.POST,
                initiateEntity,
                String.class
        );

        JsonNode initiateJson = objectMapper.readTree(initiateResponse.getBody());
        String resetToken = initiateJson.get("token").asText();

        String weakPassword = "weak";
        PasswordResetCompleteRequest completeRequest = new PasswordResetCompleteRequest(resetToken, weakPassword);
        HttpEntity<PasswordResetCompleteRequest> completeEntity = new HttpEntity<>(completeRequest);

        ResponseEntity<String> completeResponse = restTemplate.exchange(
                "/api/auth/password-reset/complete",
                HttpMethod.POST,
                completeEntity,
                String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, completeResponse.getStatusCode(),
                "Weak password should be rejected");

        UserEntity unchangedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches(oldPassword, unchangedUser.getPasswordHash()),
                "Password should not have changed");
    }
}
