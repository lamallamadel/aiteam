package com.atlasia.ai.controller;

import com.atlasia.ai.api.dto.*;
import com.atlasia.ai.model.UserEntity;
import com.atlasia.ai.persistence.UserRepository;
import com.atlasia.ai.service.AuthenticationService;
import com.atlasia.ai.service.AuthorizationService;
import com.atlasia.ai.service.OAuth2Service;
import com.atlasia.ai.service.PasswordResetService;
import com.atlasia.ai.service.RefreshTokenService;
import com.atlasia.ai.service.UserRegistrationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    private final UserRegistrationService userRegistrationService;
    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;
    private final OAuth2Service oauth2Service;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;

    public AuthController(
            UserRegistrationService userRegistrationService,
            AuthenticationService authenticationService,
            RefreshTokenService refreshTokenService,
            PasswordResetService passwordResetService,
            OAuth2Service oauth2Service,
            UserRepository userRepository,
            AuthorizationService authorizationService) {
        this.userRegistrationService = userRegistrationService;
        this.authenticationService = authenticationService;
        this.refreshTokenService = refreshTokenService;
        this.passwordResetService = passwordResetService;
        this.oauth2Service = oauth2Service;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
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
        } catch (com.atlasia.ai.service.MfaRequiredException e) {
            logger.info("MFA required for user: {}", e.getUsername());
            String mfaToken = authenticationService.generateMfaToken(e.getUserId(), e.getUsername());
            com.atlasia.ai.api.dto.MfaLoginResponse mfaResponse = 
                new com.atlasia.ai.api.dto.MfaLoginResponse(true, mfaToken);
            return ResponseEntity.ok(mfaResponse);
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

    @PostMapping("/oauth2/link")
    public ResponseEntity<?> linkOAuth2Account(
            @Valid @RequestBody OAuth2LinkRequest request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.warn("OAuth2 link attempt without authentication");
                Map<String, String> error = new HashMap<>();
                error.put("error", "Unauthorized");
                error.put("message", "User must be authenticated to link OAuth2 account");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            String token = authHeader.substring(7);
            UUID userId = extractUserIdFromToken(token);

            oauth2Service.linkOAuth2Account(
                    userId,
                    request.getProvider(),
                    request.getProviderUserId(),
                    request.getAccessToken(),
                    request.getRefreshToken()
            );

            OAuth2LinkResponse response = new OAuth2LinkResponse(
                    "OAuth2 account linked successfully",
                    request.getProvider(),
                    true
            );

            logger.info("OAuth2 account linked: userId={}, provider={}", userId, request.getProvider());
            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            logger.warn("OAuth2 link failed: {}", e.getMessage());
            OAuth2LinkResponse response = new OAuth2LinkResponse(
                    e.getMessage(),
                    request.getProvider(),
                    false
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } catch (IllegalArgumentException e) {
            logger.warn("OAuth2 link failed: {}", e.getMessage());
            OAuth2LinkResponse response = new OAuth2LinkResponse(
                    e.getMessage(),
                    request.getProvider(),
                    false
            );
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("OAuth2 link error: {}", e.getMessage(), e);
            OAuth2LinkResponse response = new OAuth2LinkResponse(
                    "Failed to link OAuth2 account",
                    request.getProvider(),
                    false
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private UUID extractUserIdFromToken(String token) {
        try {
            return authenticationService.extractUserIdFromToken(token);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid token");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserDto> getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated() || 
                "anonymousUser".equals(authentication.getPrincipal())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            UUID userId = null;
            if (authentication.getDetails() instanceof UUID) {
                userId = (UUID) authentication.getDetails();
            } else {
                String username = authentication.getName();
                UserEntity user = userRepository.findByUsername(username).orElse(null);
                if (user != null) {
                    userId = user.getId();
                }
            }

            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            UserEntity user = userRepository.findByIdWithRoles(userId)
                .orElse(null);
                
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            CurrentUserDto dto = new CurrentUserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                authorizationService.getUserRoles(userId),
                authorizationService.getUserPermissions(userId),
                user.isEnabled(),
                user.isLocked(),
                user.isMfaEnabled()
            );

            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            logger.error("Error fetching current user: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> getCsrfToken(CsrfToken csrfToken, HttpServletResponse response) {
        if (csrfToken != null) {
            Cookie cookie = new Cookie("XSRF-TOKEN", csrfToken.getToken());
            cookie.setPath("/");
            cookie.setHttpOnly(false);
            cookie.setSecure(false);
            cookie.setMaxAge(3600);
            response.addCookie(cookie);
            
            Map<String, String> tokenResponse = new HashMap<>();
            tokenResponse.put("token", csrfToken.getToken());
            tokenResponse.put("headerName", csrfToken.getHeaderName());
            tokenResponse.put("parameterName", csrfToken.getParameterName());
            
            return ResponseEntity.ok(tokenResponse);
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
