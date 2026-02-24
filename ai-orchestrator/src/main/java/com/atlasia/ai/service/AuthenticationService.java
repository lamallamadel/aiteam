package com.atlasia.ai.service;

import com.atlasia.ai.api.dto.AuthTokenResponse;
import com.atlasia.ai.api.dto.LoginRequest;
import com.atlasia.ai.config.JwtProperties;
import com.atlasia.ai.model.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;
    private final BruteForceProtectionService bruteForceProtectionService;

    public AuthenticationService(
            UserDetailsServiceImpl userDetailsService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder,
            JwtProperties jwtProperties,
            BruteForceProtectionService bruteForceProtectionService) {
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.jwtProperties = jwtProperties;
        this.bruteForceProtectionService = bruteForceProtectionService;
    }

    @Transactional
    public AuthTokenResponse authenticate(LoginRequest loginRequest) {
        try {
            if (bruteForceProtectionService.isBlocked(loginRequest.getUsername())) {
                logger.warn("Authentication blocked: Account temporarily locked due to failed attempts for user {}", 
                    loginRequest.getUsername());
                throw new LockedException("Account is temporarily locked due to multiple failed login attempts. Please try again later.");
            }

            UserEntity user = userDetailsService.loadUserEntityByUsername(loginRequest.getUsername());

            if (!user.isEnabled()) {
                logger.warn("Authentication failed: Account disabled for user {}", loginRequest.getUsername());
                bruteForceProtectionService.recordFailedLogin(loginRequest.getUsername());
                throw new DisabledException("Account is disabled");
            }

            if (user.isLocked()) {
                logger.warn("Authentication failed: Account locked for user {}", loginRequest.getUsername());
                throw new LockedException("Account is locked");
            }

            if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
                logger.warn("Authentication failed: Invalid credentials for user {}", loginRequest.getUsername());
                bruteForceProtectionService.recordFailedLogin(loginRequest.getUsername());
                throw new BadCredentialsException("Invalid username or password");
            }

            bruteForceProtectionService.resetFailedAttempts(loginRequest.getUsername());

            String accessToken = jwtService.generateAccessToken(user);
            
            Map<String, Object> refreshTokenResult = refreshTokenService.createRefreshToken(
                    user.getId(), 
                    loginRequest.getDeviceInfo()
            );
            String refreshToken = (String) refreshTokenResult.get("token");

            long expiresIn = TimeUnit.MINUTES.toSeconds(jwtProperties.getAccessTokenExpirationMinutes());

            logger.info("User authenticated successfully: {}", loginRequest.getUsername());

            return new AuthTokenResponse(accessToken, refreshToken, expiresIn);

        } catch (UsernameNotFoundException e) {
            logger.warn("Authentication failed: User not found {}", loginRequest.getUsername());
            bruteForceProtectionService.recordFailedLogin(loginRequest.getUsername());
            throw new BadCredentialsException("Invalid username or password");
        }
    }

    @Transactional
    public AuthTokenResponse refreshToken(String refreshToken) {
        Map<String, String> tokens = refreshTokenService.validateAndRotate(refreshToken);
        
        long expiresIn = TimeUnit.MINUTES.toSeconds(jwtProperties.getAccessTokenExpirationMinutes());
        
        return new AuthTokenResponse(
                tokens.get("accessToken"),
                tokens.get("refreshToken"),
                expiresIn
        );
    }

    @Transactional
    public void logout(String refreshToken) {
        try {
            refreshTokenService.revokeToken(refreshToken);
            logger.info("User logged out successfully");
        } catch (Exception e) {
            logger.warn("Logout failed: {}", e.getMessage());
        }
    }

    public java.util.UUID extractUserIdFromToken(String token) {
        return jwtService.extractUserId(token);
    }
}
