package com.atlasia.ai.controller;

import com.atlasia.ai.api.dto.*;
import com.atlasia.ai.model.UserEntity;
import com.atlasia.ai.persistence.UserRepository;
import com.atlasia.ai.service.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth/mfa")
@Validated
public class MfaController {

    private static final Logger logger = LoggerFactory.getLogger(MfaController.class);

    private final MfaService mfaService;
    private final MfaTokenService mfaTokenService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final com.atlasia.ai.config.JwtProperties jwtProperties;

    public MfaController(
            MfaService mfaService,
            MfaTokenService mfaTokenService,
            UserRepository userRepository,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            com.atlasia.ai.config.JwtProperties jwtProperties) {
        this.mfaService = mfaService;
        this.mfaTokenService = mfaTokenService;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/setup")
    public ResponseEntity<?> setupMfa() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated() || 
                "anonymousUser".equals(authentication.getPrincipal())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            String username = authentication.getName();
            UserEntity user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            String secret = mfaService.generateSecret();
            String otpAuthUrl = mfaService.generateOtpAuthUrl(secret, username);
            String qrCodeDataUri = mfaService.generateQrUri(secret, username);

            logger.info("MFA setup initiated for user: {}", username);

            MfaSetupResponse response = new MfaSetupResponse(secret, otpAuthUrl, qrCodeDataUri);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("MFA setup failed: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Setup failed");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            logger.error("MFA setup error: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Setup failed");
            error.put("message", "An error occurred during MFA setup");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/verify-setup")
    public ResponseEntity<?> verifySetup(@Valid @RequestBody MfaVerifySetupRequest request) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated() || 
                "anonymousUser".equals(authentication.getPrincipal())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            String username = authentication.getName();
            UserEntity user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            boolean isValid = mfaService.verifyCode(request.getSecret(), request.getCode());
            
            if (!isValid) {
                logger.warn("MFA verification failed for user: {}", username);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid code");
                error.put("message", "The verification code is incorrect");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            user.setMfaSecret(request.getSecret());
            userRepository.save(user);

            logger.info("MFA activated for user: {}", username);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "MFA successfully activated");
            response.put("mfaEnabled", true);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("MFA verification setup failed: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Verification failed");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            logger.error("MFA verification setup error: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Verification failed");
            error.put("message", "An error occurred during verification");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyMfa(@Valid @RequestBody MfaVerifyRequest request) {
        try {
            if (!mfaTokenService.validateMfaToken(request.getMfaToken())) {
                logger.warn("Invalid MFA token provided");
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid token");
                error.put("message", "MFA token is invalid or expired");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            UUID userId = mfaTokenService.extractUserId(request.getMfaToken());
            String username = mfaTokenService.extractUsername(request.getMfaToken());

            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            if (!user.getUsername().equals(username)) {
                logger.warn("Username mismatch in MFA token");
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid token");
                error.put("message", "Token does not match user");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            if (user.getMfaSecret() == null || user.getMfaSecret().isEmpty()) {
                logger.warn("MFA not enabled for user: {}", username);
                Map<String, String> error = new HashMap<>();
                error.put("error", "MFA not enabled");
                error.put("message", "MFA is not configured for this account");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            boolean isValid = mfaService.verifyCode(user.getMfaSecret(), request.getCode());
            
            if (!isValid) {
                logger.warn("MFA code verification failed for user: {}", username);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid code");
                error.put("message", "The verification code is incorrect");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            String accessToken = jwtService.generateAccessToken(user);
            
            Map<String, Object> refreshTokenResult = refreshTokenService.createRefreshToken(
                    user.getId(), 
                    null
            );
            String refreshToken = (String) refreshTokenResult.get("token");

            long expiresIn = TimeUnit.MINUTES.toSeconds(jwtProperties.getAccessTokenExpirationMinutes());

            logger.info("MFA verification successful for user: {}", username);

            AuthTokenResponse response = new AuthTokenResponse(accessToken, refreshToken, expiresIn);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("MFA verification failed: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Verification failed");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        } catch (Exception e) {
            logger.error("MFA verification error: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Verification failed");
            error.put("message", "An error occurred during verification");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
