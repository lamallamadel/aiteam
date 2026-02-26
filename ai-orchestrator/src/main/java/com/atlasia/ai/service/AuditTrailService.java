package com.atlasia.ai.service;

import com.atlasia.ai.model.*;
import com.atlasia.ai.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuditTrailService {

    private static final Logger logger = LoggerFactory.getLogger(AuditTrailService.class);

    private final AuditAuthenticationEventRepository authEventRepository;
    private final AuditAccessLogRepository accessLogRepository;
    private final AuditDataMutationRepository dataMutationRepository;
    private final AuditAdminActionRepository adminActionRepository;
    private final CollaborationEventRepository collaborationEventRepository;

    public AuditTrailService(
            AuditAuthenticationEventRepository authEventRepository,
            AuditAccessLogRepository accessLogRepository,
            AuditDataMutationRepository dataMutationRepository,
            AuditAdminActionRepository adminActionRepository,
            CollaborationEventRepository collaborationEventRepository) {
        this.authEventRepository = authEventRepository;
        this.accessLogRepository = accessLogRepository;
        this.dataMutationRepository = dataMutationRepository;
        this.adminActionRepository = adminActionRepository;
        this.collaborationEventRepository = collaborationEventRepository;
    }

    @Transactional
    public void logAuthenticationEvent(UUID userId, String username, String eventType,
                                      String ipAddress, String userAgent, boolean success,
                                      String failureReason) {
        Instant timestamp = Instant.now();
        AuditAuthenticationEventEntity event = new AuditAuthenticationEventEntity(
            userId, username, eventType, ipAddress, userAgent, success, failureReason, timestamp);
        
        String previousHash = authEventRepository.findLatestEvent()
            .map(AuditAuthenticationEventEntity::getEventHash)
            .orElse("0");
        
        event.setPreviousEventHash(previousHash);
        String eventHash = computeHash(event, previousHash);
        event.setEventHash(eventHash);
        
        authEventRepository.save(event);
        logger.debug("Logged authentication event for user {} with hash {}", username, eventHash);
    }

    @Transactional
    public void logAccessEvent(UUID userId, String username, String resourceType, String resourceId,
                              String action, String httpMethod, String endpoint, String ipAddress,
                              String userAgent, Integer statusCode) {
        Instant timestamp = Instant.now();
        AuditAccessLogEntity event = new AuditAccessLogEntity(
            userId, username, resourceType, resourceId, action, httpMethod, 
            endpoint, ipAddress, userAgent, statusCode, timestamp);
        
        String previousHash = accessLogRepository.findLatestEvent()
            .map(AuditAccessLogEntity::getEventHash)
            .orElse("0");
        
        event.setPreviousEventHash(previousHash);
        String eventHash = computeHash(event, previousHash);
        event.setEventHash(eventHash);
        
        accessLogRepository.save(event);
        logger.debug("Logged access event for user {} on resource {}/{}", username, resourceType, resourceId);
    }

    @Transactional
    public void logDataMutation(UUID userId, String username, String entityType, String entityId,
                               String operation, String fieldName, String oldValue, String newValue) {
        Instant timestamp = Instant.now();
        AuditDataMutationEntity event = new AuditDataMutationEntity(
            userId, username, entityType, entityId, operation, fieldName, oldValue, newValue, timestamp);
        
        String previousHash = dataMutationRepository.findLatestEvent()
            .map(AuditDataMutationEntity::getEventHash)
            .orElse("0");
        
        event.setPreviousEventHash(previousHash);
        String eventHash = computeHash(event, previousHash);
        event.setEventHash(eventHash);
        
        dataMutationRepository.save(event);
        logger.debug("Logged data mutation for entity {}/{}", entityType, entityId);
    }

    @Transactional
    public void logAdminAction(UUID adminUserId, String adminUsername, String actionType,
                              UUID targetUserId, String targetUsername, String actionDetails,
                              String ipAddress) {
        Instant timestamp = Instant.now();
        AuditAdminActionEntity event = new AuditAdminActionEntity(
            adminUserId, adminUsername, actionType, targetUserId, targetUsername, 
            actionDetails, ipAddress, timestamp);
        
        String previousHash = adminActionRepository.findLatestEvent()
            .map(AuditAdminActionEntity::getEventHash)
            .orElse("0");
        
        event.setPreviousEventHash(previousHash);
        String eventHash = computeHash(event, previousHash);
        event.setEventHash(eventHash);
        
        adminActionRepository.save(event);
        logger.info("Logged admin action {} by {} on target {}", actionType, adminUsername, targetUsername);
    }

    @Transactional
    public void updateCollaborationEventHash(CollaborationEventEntity event) {
        String previousHash = collaborationEventRepository.findLatestEvent()
            .map(CollaborationEventEntity::getEventHash)
            .orElse("0");
        
        event.setPreviousEventHash(previousHash);
        String eventHash = computeHash(event, previousHash);
        event.setEventHash(eventHash);
    }

    private String computeHash(Object event, String previousHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            StringBuilder dataToHash = new StringBuilder();
            dataToHash.append(previousHash).append("|");
            
            if (event instanceof AuditAuthenticationEventEntity) {
                AuditAuthenticationEventEntity e = (AuditAuthenticationEventEntity) event;
                dataToHash.append(e.getUsername()).append("|")
                         .append(e.getEventType()).append("|")
                         .append(e.getTimestamp().toString()).append("|")
                         .append(e.isSuccess());
            } else if (event instanceof AuditAccessLogEntity) {
                AuditAccessLogEntity e = (AuditAccessLogEntity) event;
                dataToHash.append(e.getUsername()).append("|")
                         .append(e.getResourceType()).append("|")
                         .append(e.getResourceId()).append("|")
                         .append(e.getAction()).append("|")
                         .append(e.getTimestamp().toString());
            } else if (event instanceof AuditDataMutationEntity) {
                AuditDataMutationEntity e = (AuditDataMutationEntity) event;
                dataToHash.append(e.getUsername()).append("|")
                         .append(e.getEntityType()).append("|")
                         .append(e.getEntityId()).append("|")
                         .append(e.getOperation()).append("|")
                         .append(e.getTimestamp().toString());
            } else if (event instanceof AuditAdminActionEntity) {
                AuditAdminActionEntity e = (AuditAdminActionEntity) event;
                dataToHash.append(e.getAdminUsername()).append("|")
                         .append(e.getActionType()).append("|")
                         .append(e.getTimestamp().toString());
            } else if (event instanceof CollaborationEventEntity) {
                CollaborationEventEntity e = (CollaborationEventEntity) event;
                dataToHash.append(e.getUserId()).append("|")
                         .append(e.getEventType()).append("|")
                         .append(e.getRunId().toString()).append("|")
                         .append(e.getTimestamp().toString());
            }
            
            byte[] hashBytes = digest.digest(dataToHash.toString().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Failed to compute event hash", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public boolean verifyHashChain(String tableName) {
        switch (tableName) {
            case "authentication":
                return verifyAuthenticationHashChain();
            case "access_logs":
                return verifyAccessLogHashChain();
            case "data_mutations":
                return verifyDataMutationHashChain();
            case "admin_actions":
                return verifyAdminActionHashChain();
            default:
                return false;
        }
    }

    private boolean verifyAuthenticationHashChain() {
        var events = authEventRepository.findAll();
        return verifyChain(events, AuditAuthenticationEventEntity::getEventHash, 
                          AuditAuthenticationEventEntity::getPreviousEventHash);
    }

    private boolean verifyAccessLogHashChain() {
        var events = accessLogRepository.findAll();
        return verifyChain(events, AuditAccessLogEntity::getEventHash, 
                          AuditAccessLogEntity::getPreviousEventHash);
    }

    private boolean verifyDataMutationHashChain() {
        var events = dataMutationRepository.findAll();
        return verifyChain(events, AuditDataMutationEntity::getEventHash, 
                          AuditDataMutationEntity::getPreviousEventHash);
    }

    private boolean verifyAdminActionHashChain() {
        var events = adminActionRepository.findAll();
        return verifyChain(events, AuditAdminActionEntity::getEventHash, 
                          AuditAdminActionEntity::getPreviousEventHash);
    }

    private <T> boolean verifyChain(java.util.List<T> events, 
                                    java.util.function.Function<T, String> getHash,
                                    java.util.function.Function<T, String> getPrevHash) {
        if (events.isEmpty()) {
            return true;
        }
        
        String expectedPrevHash = "0";
        for (T event : events) {
            String prevHash = getPrevHash.apply(event);
            if (!expectedPrevHash.equals(prevHash)) {
                logger.error("Hash chain verification failed. Expected previous hash: {}, got: {}", 
                           expectedPrevHash, prevHash);
                return false;
            }
            
            String computedHash = computeHash(event, prevHash);
            String storedHash = getHash.apply(event);
            if (!computedHash.equals(storedHash)) {
                logger.error("Hash verification failed. Computed: {}, stored: {}", 
                           computedHash, storedHash);
                return false;
            }
            
            expectedPrevHash = storedHash;
        }
        
        return true;
    }
}
