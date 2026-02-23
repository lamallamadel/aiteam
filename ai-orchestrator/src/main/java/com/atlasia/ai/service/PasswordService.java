package com.atlasia.ai.service;

import com.atlasia.ai.model.PasswordHistoryEntity;
import com.atlasia.ai.persistence.PasswordHistoryRepository;
import org.passay.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class PasswordService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordService.class);
    private static final int PASSWORD_HISTORY_LIMIT = 5;
    
    private final PasswordEncoder passwordEncoder;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordValidator passwordValidator;

    public PasswordService(
            PasswordEncoder passwordEncoder,
            PasswordHistoryRepository passwordHistoryRepository) {
        this.passwordEncoder = passwordEncoder;
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.passwordValidator = new PasswordValidator(Arrays.asList(
            new LengthRule(12, 128),
            new CharacterRule(EnglishCharacterData.UpperCase, 1),
            new CharacterRule(EnglishCharacterData.LowerCase, 1),
            new CharacterRule(EnglishCharacterData.Digit, 1),
            new CharacterRule(EnglishCharacterData.Special, 1),
            new WhitespaceRule()
        ));
    }

    public String hashPassword(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        return passwordEncoder.encode(plaintext);
    }

    public boolean validatePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            return false;
        }
        
        RuleResult result = passwordValidator.validate(new PasswordData(password));
        
        if (!result.isValid()) {
            List<String> messages = passwordValidator.getMessages(result);
            logger.debug("Password validation failed: {}", String.join(", ", messages));
        }
        
        return result.isValid();
    }

    public String getPasswordStrengthErrors(String password) {
        if (password == null || password.isEmpty()) {
            return "Password cannot be null or empty";
        }
        
        RuleResult result = passwordValidator.validate(new PasswordData(password));
        
        if (!result.isValid()) {
            List<String> messages = passwordValidator.getMessages(result);
            return String.join("; ", messages);
        }
        
        return null;
    }

    @Transactional(readOnly = true)
    public boolean checkPasswordHistory(UUID userId, String newPassword) {
        List<PasswordHistoryEntity> history = passwordHistoryRepository
            .findByUserIdOrderByCreatedAtDesc(userId);
        
        int checkLimit = Math.min(PASSWORD_HISTORY_LIMIT, history.size());
        
        for (int i = 0; i < checkLimit; i++) {
            PasswordHistoryEntity entry = history.get(i);
            if (passwordEncoder.matches(newPassword, entry.getPasswordHash())) {
                logger.debug("Password matches history entry for user: {}", userId);
                return false;
            }
        }
        
        return true;
    }

    @Transactional
    public void savePasswordToHistory(UUID userId, String passwordHash) {
        PasswordHistoryEntity historyEntry = new PasswordHistoryEntity(userId, passwordHash);
        passwordHistoryRepository.save(historyEntry);
        logger.debug("Saved password to history for user: {}", userId);
    }

    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
