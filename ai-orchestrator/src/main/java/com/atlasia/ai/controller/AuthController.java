package com.atlasia.ai.controller;

import com.atlasia.ai.api.dto.*;
import com.atlasia.ai.service.AuthenticationService;
import com.atlasia.ai.service.PasswordResetService;
import com.atlasia.ai.service.RefreshTokenService;
import com.atlasia.ai.service.UserRegistrationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    private final UserRegistrationService userRegistrationService;
    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;

    public AuthController(
            UserRegistrationService userRegistrationService,
            AuthenticationService authenticationService,
            RefreshTokenService refreshTokenService,
            PasswordResetService passwordResetService) {
        this.userRegistrationService = userRegistrationService;
        this.authenticationService = authenticationService;
        this.refreshTokenService = refreshTokenService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserRegistrationResponse> register(
            @Valid @RequestBody UserRegistrationRequest request) {
        try {
            UserRegistrationResponse response = userRegistrationService.registerUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Registration failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                new UserRegistrationResponse(null, null, null, e.getMessage())
            );
        } catch (Exception e) {
            logger.error("Registration error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new UserRegistrationResponse(null, null, null, "Registration failed")
            );
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthTokenResponse response = authenticationService.authenticate(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Login failed: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid credentials");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        } catch (Exception e) {
            logger.error("Login error: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Authentication failed");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            AuthTokenResponse response = authenticationService.refreshToken(request.getRefreshToken());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Token refresh failed: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid refresh token");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        } catch (Exception e) {
            logger.error("Token refresh error: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Token refresh failed");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            authenticationService.logout(request.getRefreshToken());
            Map<String, String> response = new HashMap<>();
            response.put("message", "Logged out successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.warn("Logout error: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("message", "Logged out");
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/password-reset/initiate")
    public ResponseEntity<?> initiatePasswordReset(
            @Valid @RequestBody PasswordResetInitiateRequest request) {
        try {
            String resetToken = passwordResetService.initiateReset(request.getEmail());
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Password reset initiated. Check your email for instructions.");
            response.put("token", resetToken);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Password reset initiation failed: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid request");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            logger.error("Password reset initiation error: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Password reset failed");
            error.put("message", "An error occurred while processing your request");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/password-reset/complete")
    public ResponseEntity<?> completePasswordReset(
            @Valid @RequestBody PasswordResetCompleteRequest request) {
        try {
            passwordResetService.completeReset(request.getToken(), request.getNewPassword());
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Password reset successfully");
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Password reset completion failed: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid request");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            logger.error("Password reset completion error: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Password reset failed");
            error.put("message", "An error occurred while processing your request");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
