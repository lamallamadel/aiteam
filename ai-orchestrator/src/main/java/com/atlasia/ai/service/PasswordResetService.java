package com.atlasia.ai.service;

import com.atlasia.ai.model.PasswordResetTokenEntity;
import com.atlasia.ai.model.UserEntity;
import com.atlasia.ai.persistence.PasswordResetTokenRepository;
import com.atlasia.ai.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_EXPIRATION_HOURS = 1;
    private static final int TOKEN_LENGTH = 32;
    
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordService passwordService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository resetTokenRepository,
            PasswordService passwordService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.passwordService = passwordService;
        this.passwordEncoder = passwordEncoder;
        this.secureRandom = new SecureRandom();
    }

    @Transactional
    public String initiateReset(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        
        final String normalizedEmail = email.trim().toLowerCase();
        
        UserEntity user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + normalizedEmail));
        
        if (!user.isEnabled()) {
            throw new IllegalStateException("User account is disabled");
        }
        
        if (user.isLocked()) {
            throw new IllegalStateException("User account is locked");
        }
        
        resetTokenRepository.invalidateAllUserTokens(user.getId());
        
        String resetToken = generateSecureToken();
        String tokenHash = passwordEncoder.encode(resetToken);
        Instant expiresAt = Instant.now().plus(TOKEN_EXPIRATION_HOURS, ChronoUnit.HOURS);
        
        PasswordResetTokenEntity tokenEntity = new PasswordResetTokenEntity(
            user.getId(),
            tokenHash,
            expiresAt
        );
        
        resetTokenRepository.save(tokenEntity);
        
        logger.info("Password reset initiated for user: {}", user.getUsername());
        
        return resetToken;
    }

    @Transactional(readOnly = true)
    public boolean validateResetToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        
        try {
            PasswordResetTokenEntity tokenEntity = resetTokenRepository.findAll().stream()
                .filter(t -> passwordEncoder.matches(token, t.getTokenHash()))
                .findFirst()
                .orElse(null);
            
            if (tokenEntity == null) {
                return false;
            }
            
            return tokenEntity.isValid();
        } catch (Exception e) {
            logger.error("Error validating reset token: {}", e.getMessage());
            return false;
        }
    }

    @Transactional
    public void completeReset(String token, String newPassword) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Token cannot be empty");
        }
        
        if (newPassword == null || newPassword.isEmpty()) {
            throw new IllegalArgumentException("New password cannot be empty");
        }
        
        PasswordResetTokenEntity tokenEntity = resetTokenRepository.findAll().stream()
            .filter(t -> passwordEncoder.matches(token, t.getTokenHash()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));
        
        if (!tokenEntity.isValid()) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }
        
        UserEntity user = userRepository.findById(tokenEntity.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        if (!user.isEnabled()) {
            throw new IllegalStateException("User account is disabled");
        }
        
        if (user.isLocked()) {
            throw new IllegalStateException("User account is locked");
        }
        
        if (!passwordService.validatePasswordStrength(newPassword)) {
            String errors = passwordService.getPasswordStrengthErrors(newPassword);
            throw new IllegalArgumentException("Password does not meet strength requirements: " + errors);
        }
        
        if (!passwordService.checkPasswordHistory(user.getId(), newPassword)) {
            throw new IllegalArgumentException("Password has been used recently. Please choose a different password.");
        }
        
        String newPasswordHash = passwordService.hashPassword(newPassword);
        
        user.setPasswordHash(newPasswordHash);
        userRepository.save(user);
        
        passwordService.savePasswordToHistory(user.getId(), newPasswordHash);
        
        tokenEntity.setUsed(true);
        resetTokenRepository.save(tokenEntity);
        
        resetTokenRepository.invalidateAllUserTokens(user.getId());
        
        logger.info("Password reset completed for user: {}", user.getUsername());
    }

    private String generateSecureToken() {
        byte[] tokenBytes = new byte[TOKEN_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = resetTokenRepository.deleteExpiredTokens(Instant.now());
        logger.info("Cleaned up {} expired password reset tokens", deleted);
    }
}
