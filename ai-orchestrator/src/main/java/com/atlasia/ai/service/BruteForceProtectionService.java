package com.atlasia.ai.service;

import com.atlasia.ai.model.UserEntity;
import com.atlasia.ai.persistence.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BruteForceProtectionService {

    private static final Logger logger = LoggerFactory.getLogger(BruteForceProtectionService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;
    
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final Cache<String, FailedLoginAttempt> failedLoginCache;
    private final Counter blockedAccountCounter;
    private final Counter failedLoginCounter;

    public BruteForceProtectionService(
            UserRepository userRepository,
            @Autowired(required = false) JavaMailSender mailSender,
            MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.failedLoginCache = Caffeine.newBuilder()
                .expireAfterWrite(LOCK_DURATION_MINUTES, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();
        this.blockedAccountCounter = Counter.builder("bruteforce.blocked.accounts")
                .description("Number of accounts blocked due to brute force attempts")
                .register(meterRegistry);
        this.failedLoginCounter = Counter.builder("bruteforce.failed.logins")
                .description("Number of failed login attempts")
                .register(meterRegistry);
    }

    public boolean isBlocked(String username) {
        FailedLoginAttempt attempt = failedLoginCache.getIfPresent(username);
        if (attempt == null) {
            return false;
        }
        
        if (attempt.getAttempts() >= MAX_FAILED_ATTEMPTS) {
            Instant lockExpiry = attempt.getFirstAttemptTime().plus(LOCK_DURATION_MINUTES, ChronoUnit.MINUTES);
            if (Instant.now().isBefore(lockExpiry)) {
                logger.debug("Account {} is blocked until {}", username, lockExpiry);
                return true;
            } else {
                failedLoginCache.invalidate(username);
                return false;
            }
        }
        
        return false;
    }

    @Transactional
    public void recordFailedLogin(String username) {
        failedLoginCounter.increment();
        
        FailedLoginAttempt attempt = failedLoginCache.get(username, 
            k -> new FailedLoginAttempt(Instant.now()));
        
        if (attempt != null) {
            int attempts = attempt.incrementAndGet();
            logger.warn("Failed login attempt {} for user: {}", attempts, username);
            
            if (attempts == MAX_FAILED_ATTEMPTS) {
                lockAccount(username);
                blockedAccountCounter.increment();
                sendLockNotificationEmail(username);
                logger.error("Account locked due to {} failed login attempts: {}", MAX_FAILED_ATTEMPTS, username);
            }
        }
    }

    @Transactional
    public void resetFailedAttempts(String username) {
        failedLoginCache.invalidate(username);
        logger.info("Failed login attempts reset for user: {}", username);
    }

    @Transactional
    public void unlockAccount(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setLocked(false);
            userRepository.save(user);
            failedLoginCache.invalidate(username);
            logger.info("Account unlocked: {}", username);
        });
    }

    private void lockAccount(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setLocked(true);
            userRepository.save(user);
        });
    }

    private void sendLockNotificationEmail(String username) {
        if (mailSender == null) {
            logger.warn("Mail sender not configured. Skipping email notification for user: {}", username);
            return;
        }
        
        try {
            userRepository.findByUsername(username).ifPresent(user -> {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(user.getEmail());
                message.setSubject("Account Locked - Security Alert");
                message.setText(String.format(
                    "Hello %s,\n\n" +
                    "Your account has been temporarily locked due to %d failed login attempts.\n" +
                    "This is a security measure to protect your account.\n\n" +
                    "The account will be automatically unlocked after %d minutes.\n" +
                    "If you did not attempt to log in, please contact support immediately.\n\n" +
                    "To unlock your account immediately, please contact an administrator.\n\n" +
                    "Best regards,\n" +
                    "Atlasia AI Orchestrator Security Team",
                    username, MAX_FAILED_ATTEMPTS, LOCK_DURATION_MINUTES
                ));
                
                mailSender.send(message);
                logger.info("Lock notification email sent to: {}", user.getEmail());
            });
        } catch (Exception e) {
            logger.error("Failed to send lock notification email for user: {}", username, e);
        }
    }

    public int getFailedAttempts(String username) {
        FailedLoginAttempt attempt = failedLoginCache.getIfPresent(username);
        return attempt != null ? attempt.getAttempts() : 0;
    }

    private static class FailedLoginAttempt {
        private final AtomicInteger attempts = new AtomicInteger(0);
        private final Instant firstAttemptTime;

        public FailedLoginAttempt(Instant firstAttemptTime) {
            this.firstAttemptTime = firstAttemptTime;
        }

        public int incrementAndGet() {
            return attempts.incrementAndGet();
        }

        public int getAttempts() {
            return attempts.get();
        }

        public Instant getFirstAttemptTime() {
            return firstAttemptTime;
        }
    }
}
