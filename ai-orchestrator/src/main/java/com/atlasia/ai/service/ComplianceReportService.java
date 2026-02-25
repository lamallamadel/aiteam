package com.atlasia.ai.service;

import com.atlasia.ai.model.*;
import com.atlasia.ai.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ComplianceReportService {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceReportService.class);

    private final UserRepository userRepository;
    private final AuditAuthenticationEventRepository authEventRepository;
    private final AuditAccessLogRepository accessLogRepository;
    private final AuditDataMutationRepository dataMutationRepository;
    private final AuditAdminActionRepository adminActionRepository;
    private final CollaborationEventRepository collaborationEventRepository;
    private final ComplianceReportRepository complianceReportRepository;
    private final ObjectMapper objectMapper;

    public ComplianceReportService(
            UserRepository userRepository,
            AuditAuthenticationEventRepository authEventRepository,
            AuditAccessLogRepository accessLogRepository,
            AuditDataMutationRepository dataMutationRepository,
            AuditAdminActionRepository adminActionRepository,
            CollaborationEventRepository collaborationEventRepository,
            ComplianceReportRepository complianceReportRepository) {
        this.userRepository = userRepository;
        this.authEventRepository = authEventRepository;
        this.accessLogRepository = accessLogRepository;
        this.dataMutationRepository = dataMutationRepository;
        this.adminActionRepository = adminActionRepository;
        this.collaborationEventRepository = collaborationEventRepository;
        this.complianceReportRepository = complianceReportRepository;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportUserDataForGDPR(UUID userId) {
        logger.info("Generating GDPR data export for user {}", userId);
        
        Map<String, Object> exportData = new HashMap<>();
        exportData.put("exportTimestamp", Instant.now());
        exportData.put("userId", userId);
        
        userRepository.findById(userId).ifPresent(user -> {
            Map<String, Object> userData = new HashMap<>();
            userData.put("username", user.getUsername());
            userData.put("email", user.getEmail());
            userData.put("createdAt", user.getCreatedAt());
            userData.put("updatedAt", user.getUpdatedAt());
            userData.put("enabled", user.isEnabled());
            userData.put("mfaEnabled", user.isMfaEnabled());
            exportData.put("user", userData);
        });
        
        List<Map<String, Object>> authEvents = authEventRepository.findByUserIdOrderByTimestampDesc(userId)
            .stream()
            .map(this::convertAuthEventToMap)
            .collect(Collectors.toList());
        exportData.put("authenticationEvents", authEvents);
        
        List<Map<String, Object>> accessLogs = accessLogRepository.findByUserIdOrderByTimestampDesc(userId)
            .stream()
            .map(this::convertAccessLogToMap)
            .collect(Collectors.toList());
        exportData.put("accessLogs", accessLogs);
        
        List<Map<String, Object>> dataMutations = dataMutationRepository.findByUserIdOrderByTimestampDesc(userId)
            .stream()
            .map(this::convertDataMutationToMap)
            .collect(Collectors.toList());
        exportData.put("dataMutations", dataMutations);
        
        List<Map<String, Object>> adminActionsAsTarget = adminActionRepository.findByTargetUserIdOrderByTimestampDesc(userId)
            .stream()
            .map(this::convertAdminActionToMap)
            .collect(Collectors.toList());
        exportData.put("adminActionsAsTarget", adminActionsAsTarget);
        
        List<Map<String, Object>> adminActionsAsAdmin = adminActionRepository.findByAdminUserIdOrderByTimestampDesc(userId)
            .stream()
            .map(this::convertAdminActionToMap)
            .collect(Collectors.toList());
        exportData.put("adminActionsAsAdmin", adminActionsAsAdmin);
        
        List<Map<String, Object>> collaborationEvents = collaborationEventRepository.findByUserIdOrderByTimestampAsc(userId.toString())
            .stream()
            .map(this::convertCollaborationEventToMap)
            .collect(Collectors.toList());
        exportData.put("collaborationEvents", collaborationEvents);
        
        exportData.put("totalRecords", 
            authEvents.size() + accessLogs.size() + dataMutations.size() + 
            adminActionsAsTarget.size() + adminActionsAsAdmin.size() + collaborationEvents.size());
        
        logger.info("GDPR export completed for user {} with {} total records", 
                   userId, exportData.get("totalRecords"));
        
        return exportData;
    }

    @Transactional
    public String generateSOC2Report(Instant periodStart, Instant periodEnd, String generatedBy) {
        logger.info("Generating SOC2 compliance report for period {} to {}", periodStart, periodEnd);
        
        try {
            String fileName = String.format("soc2_report_%s_%s.csv", 
                periodStart.toString().substring(0, 10), 
                periodEnd.toString().substring(0, 10));
            String filePath = "compliance-reports/" + fileName;
            
            File directory = new File("compliance-reports");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            
            File csvFile = new File(filePath);
            int recordCount = 0;
            
            try (FileWriter writer = new FileWriter(csvFile)) {
                writer.append("Event Type,Timestamp,User,Resource,Action,Status,IP Address,Details\n");
                
                List<AuditAuthenticationEventEntity> authEvents = 
                    authEventRepository.findByTimestampBetweenOrderByTimestampAsc(periodStart, periodEnd);
                for (AuditAuthenticationEventEntity event : authEvents) {
                    writer.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        "AUTHENTICATION",
                        event.getTimestamp().toString(),
                        escapeCSV(event.getUsername()),
                        "USER_ACCOUNT",
                        event.getEventType(),
                        event.isSuccess() ? "SUCCESS" : "FAILURE",
                        escapeCSV(event.getIpAddress()),
                        escapeCSV(event.getFailureReason())
                    ));
                    recordCount++;
                }
                
                List<AuditAccessLogEntity> accessLogs = 
                    accessLogRepository.findByTimestampBetweenOrderByTimestampAsc(periodStart, periodEnd);
                for (AuditAccessLogEntity event : accessLogs) {
                    writer.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        "ACCESS_LOG",
                        event.getTimestamp().toString(),
                        escapeCSV(event.getUsername()),
                        escapeCSV(event.getResourceType() + "/" + event.getResourceId()),
                        event.getAction(),
                        String.valueOf(event.getStatusCode()),
                        escapeCSV(event.getIpAddress()),
                        escapeCSV(event.getEndpoint())
                    ));
                    recordCount++;
                }
                
                List<AuditDataMutationEntity> dataMutations = 
                    dataMutationRepository.findByTimestampBetweenOrderByTimestampAsc(periodStart, periodEnd);
                for (AuditDataMutationEntity event : dataMutations) {
                    writer.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        "DATA_MUTATION",
                        event.getTimestamp().toString(),
                        escapeCSV(event.getUsername()),
                        escapeCSV(event.getEntityType() + "/" + event.getEntityId()),
                        event.getOperation(),
                        "N/A",
                        "N/A",
                        escapeCSV(event.getFieldName())
                    ));
                    recordCount++;
                }
                
                List<AuditAdminActionEntity> adminActions = 
                    adminActionRepository.findByTimestampBetweenOrderByTimestampAsc(periodStart, periodEnd);
                for (AuditAdminActionEntity event : adminActions) {
                    writer.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        "ADMIN_ACTION",
                        event.getTimestamp().toString(),
                        escapeCSV(event.getAdminUsername()),
                        escapeCSV("USER/" + event.getTargetUsername()),
                        event.getActionType(),
                        "N/A",
                        escapeCSV(event.getIpAddress()),
                        escapeCSV(event.getActionDetails())
                    ));
                    recordCount++;
                }
            }
            
            ComplianceReportEntity report = new ComplianceReportEntity(
                "SOC2", periodStart, periodEnd, Instant.now(), generatedBy, 
                filePath, recordCount, "COMPLETED");
            complianceReportRepository.save(report);
            
            logger.info("SOC2 report generated successfully: {} with {} records", filePath, recordCount);
            return filePath;
            
        } catch (IOException e) {
            logger.error("Failed to generate SOC2 report", e);
            ComplianceReportEntity report = new ComplianceReportEntity(
                "SOC2", periodStart, periodEnd, Instant.now(), generatedBy, 
                null, 0, "FAILED");
            complianceReportRepository.save(report);
            throw new RuntimeException("Failed to generate SOC2 report", e);
        }
    }

    @Transactional
    public String generateISO27001Report(Instant periodStart, Instant periodEnd, String generatedBy) {
        logger.info("Generating ISO 27001 compliance report for period {} to {}", periodStart, periodEnd);
        
        try {
            String fileName = String.format("iso27001_report_%s_%s.csv", 
                periodStart.toString().substring(0, 10), 
                periodEnd.toString().substring(0, 10));
            String filePath = "compliance-reports/" + fileName;
            
            File directory = new File("compliance-reports");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            
            File csvFile = new File(filePath);
            int recordCount = 0;
            
            try (FileWriter writer = new FileWriter(csvFile)) {
                writer.append("Control Domain,Event Type,Timestamp,User,Action,Resource,Status,Evidence\n");
                
                List<AuditAuthenticationEventEntity> authEvents = 
                    authEventRepository.findByTimestampBetweenOrderByTimestampAsc(periodStart, periodEnd);
                for (AuditAuthenticationEventEntity event : authEvents) {
                    writer.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        "A.9.4.2 Access Control",
                        "AUTHENTICATION",
                        event.getTimestamp().toString(),
                        escapeCSV(event.getUsername()),
                        event.getEventType(),
                        "Authentication System",
                        event.isSuccess() ? "SUCCESS" : "FAILURE",
                        escapeCSV("IP: " + event.getIpAddress())
                    ));
                    recordCount++;
                }
                
                List<AuditAccessLogEntity> accessLogs = 
                    accessLogRepository.findByTimestampBetweenOrderByTimestampAsc(periodStart, periodEnd);
                for (AuditAccessLogEntity event : accessLogs) {
                    writer.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        "A.9.4.1 Information Access Restriction",
                        "ACCESS_LOG",
                        event.getTimestamp().toString(),
                        escapeCSV(event.getUsername()),
                        event.getAction(),
                        escapeCSV(event.getResourceType() + "/" + event.getResourceId()),
                        "HTTP " + event.getStatusCode(),
                        escapeCSV(event.getEndpoint())
                    ));
                    recordCount++;
                }
                
                List<AuditDataMutationEntity> dataMutations = 
                    dataMutationRepository.findByTimestampBetweenOrderByTimestampAsc(periodStart, periodEnd);
                for (AuditDataMutationEntity event : dataMutations) {
                    writer.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        "A.12.4.1 Event Logging",
                        "DATA_MUTATION",
                        event.getTimestamp().toString(),
                        escapeCSV(event.getUsername()),
                        event.getOperation(),
                        escapeCSV(event.getEntityType() + "/" + event.getEntityId()),
                        "LOGGED",
                        escapeCSV("Field: " + event.getFieldName())
                    ));
                    recordCount++;
                }
                
                List<AuditAdminActionEntity> adminActions = 
                    adminActionRepository.findByTimestampBetweenOrderByTimestampAsc(periodStart, periodEnd);
                for (AuditAdminActionEntity event : adminActions) {
                    writer.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        "A.9.2.3 Privileged Access Management",
                        "ADMIN_ACTION",
                        event.getTimestamp().toString(),
                        escapeCSV(event.getAdminUsername()),
                        event.getActionType(),
                        escapeCSV("Target: " + event.getTargetUsername()),
                        "EXECUTED",
                        escapeCSV(event.getActionDetails())
                    ));
                    recordCount++;
                }
            }
            
            ComplianceReportEntity report = new ComplianceReportEntity(
                "ISO27001", periodStart, periodEnd, Instant.now(), generatedBy, 
                filePath, recordCount, "COMPLETED");
            complianceReportRepository.save(report);
            
            logger.info("ISO 27001 report generated successfully: {} with {} records", filePath, recordCount);
            return filePath;
            
        } catch (IOException e) {
            logger.error("Failed to generate ISO 27001 report", e);
            ComplianceReportEntity report = new ComplianceReportEntity(
                "ISO27001", periodStart, periodEnd, Instant.now(), generatedBy, 
                null, 0, "FAILED");
            complianceReportRepository.save(report);
            throw new RuntimeException("Failed to generate ISO 27001 report", e);
        }
    }

    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "\"\"");
    }

    private Map<String, Object> convertAuthEventToMap(AuditAuthenticationEventEntity event) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", event.getId());
        map.put("eventType", event.getEventType());
        map.put("timestamp", event.getTimestamp());
        map.put("success", event.isSuccess());
        map.put("ipAddress", event.getIpAddress());
        map.put("userAgent", event.getUserAgent());
        map.put("failureReason", event.getFailureReason());
        map.put("eventHash", event.getEventHash());
        return map;
    }

    private Map<String, Object> convertAccessLogToMap(AuditAccessLogEntity event) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", event.getId());
        map.put("resourceType", event.getResourceType());
        map.put("resourceId", event.getResourceId());
        map.put("action", event.getAction());
        map.put("httpMethod", event.getHttpMethod());
        map.put("endpoint", event.getEndpoint());
        map.put("timestamp", event.getTimestamp());
        map.put("statusCode", event.getStatusCode());
        map.put("ipAddress", event.getIpAddress());
        map.put("eventHash", event.getEventHash());
        return map;
    }

    private Map<String, Object> convertDataMutationToMap(AuditDataMutationEntity event) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", event.getId());
        map.put("entityType", event.getEntityType());
        map.put("entityId", event.getEntityId());
        map.put("operation", event.getOperation());
        map.put("fieldName", event.getFieldName());
        map.put("oldValue", event.getOldValue());
        map.put("newValue", event.getNewValue());
        map.put("timestamp", event.getTimestamp());
        map.put("eventHash", event.getEventHash());
        return map;
    }

    private Map<String, Object> convertAdminActionToMap(AuditAdminActionEntity event) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", event.getId());
        map.put("adminUsername", event.getAdminUsername());
        map.put("actionType", event.getActionType());
        map.put("targetUsername", event.getTargetUsername());
        map.put("actionDetails", event.getActionDetails());
        map.put("timestamp", event.getTimestamp());
        map.put("ipAddress", event.getIpAddress());
        map.put("eventHash", event.getEventHash());
        return map;
    }

    private Map<String, Object> convertCollaborationEventToMap(CollaborationEventEntity event) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", event.getId());
        map.put("runId", event.getRunId());
        map.put("eventType", event.getEventType());
        map.put("eventData", event.getEventData());
        map.put("timestamp", event.getTimestamp());
        map.put("eventHash", event.getEventHash());
        return map;
    }
}
