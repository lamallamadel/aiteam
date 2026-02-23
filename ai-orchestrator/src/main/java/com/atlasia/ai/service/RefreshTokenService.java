package com.atlasia.ai.service;

import com.atlasia.ai.model.RefreshTokenEntity;
import com.atlasia.ai.model.UserEntity;
import com.atlasia.ai.persistence.RefreshTokenRepository;
import com.atlasia.ai.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);
    
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Map<String, Object> createRefreshToken(UUID userId, String deviceInfo) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        
        String refreshToken = jwtService.generateRefreshToken(user);
        String tokenHash = passwordEncoder.encode(refreshToken);
        
        Instant expiresAt = jwtService.getTokenExpiration(refreshToken);
        
        RefreshTokenEntity tokenEntity = new RefreshTokenEntity(
                userId,
                tokenHash,
                expiresAt,
                deviceInfo
        );
        
        RefreshTokenEntity saved = refreshTokenRepository.save(tokenEntity);
        logger.info("Created refresh token for user: {}", userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("token", refreshToken);
        result.put("entity", saved);
        return result;
    }

    @Transactional
    public Map<String, String> validateAndRotate(String tokenString) {
        if (!jwtService.validateToken(tokenString)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        
        UUID userId = jwtService.extractUserId(tokenString);
        
        RefreshTokenEntity oldToken = refreshTokenRepository.findByUserId(userId).stream()
                .filter(token -> passwordEncoder.matches(tokenString, token.getTokenHash()))
                .filter(RefreshTokenEntity::isValid)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found or invalid"));
        
        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);
        
        UserEntity user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        
        if (!user.isEnabled()) {
            throw new IllegalStateException("User account is disabled");
        }
        
        if (user.isLocked()) {
            throw new IllegalStateException("User account is locked");
        }
        
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);
        
        String newTokenHash = passwordEncoder.encode(newRefreshToken);
        Instant newExpiresAt = jwtService.getTokenExpiration(newRefreshToken);
        
        RefreshTokenEntity newTokenEntity = new RefreshTokenEntity(
                userId,
                newTokenHash,
                newExpiresAt,
                oldToken.getDeviceInfo()
        );
        refreshTokenRepository.save(newTokenEntity);
        
        logger.info("Rotated refresh token for user: {}", userId);
        
        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", newAccessToken);
        tokens.put("refreshToken", newRefreshToken);
        return tokens;
    }

    @Transactional
    public int revokeAllUserTokens(UUID userId) {
        int count = refreshTokenRepository.revokeAllByUserId(userId);
        logger.info("Revoked {} refresh tokens for user: {}", count, userId);
        return count;
    }

    @Transactional
    public void revokeToken(String tokenString) {
        if (!jwtService.validateToken(tokenString)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        
        UUID userId = jwtService.extractUserId(tokenString);
        
        RefreshTokenEntity token = refreshTokenRepository.findByUserId(userId).stream()
                .filter(t -> passwordEncoder.matches(tokenString, t.getTokenHash()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));
        
        token.setRevoked(true);
        refreshTokenRepository.save(token);
        logger.info("Revoked refresh token for user: {}", userId);
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredTokens(Instant.now());
        logger.info("Cleaned up {} expired refresh tokens", deleted);
    }
}
