package com.atlasia.ai.service;

import com.atlasia.ai.api.dto.*;
import com.atlasia.ai.model.NotificationConfigEntity;
import com.atlasia.ai.model.NotificationDeliveryLogEntity;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.persistence.NotificationConfigRepository;
import com.atlasia.ai.persistence.NotificationDeliveryLogRepository;
import com.atlasia.ai.persistence.RunArtifactRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final NotificationConfigRepository notificationConfigRepository;
    private final NotificationDeliveryLogRepository notificationDeliveryLogRepository;
    private final RunArtifactRepository runArtifactRepository;
    private final AnalyticsService analyticsService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationConfigRepository notificationConfigRepository,
                               NotificationDeliveryLogRepository notificationDeliveryLogRepository,
                               RunArtifactRepository runArtifactRepository,
                               AnalyticsService analyticsService,
                               WebClient.Builder webClientBuilder,
                               ObjectMapper objectMapper) {
        this.notificationConfigRepository = notificationConfigRepository;
        this.notificationDeliveryLogRepository = notificationDeliveryLogRepository;
        this.runArtifactRepository = runArtifactRepository;
        this.analyticsService = analyticsService;
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NotificationConfigDto createNotificationConfig(UUID userId, CreateNotificationConfigDto dto) {
        String enabledEventsJson;
        try {
            enabledEventsJson = objectMapper.writeValueAsString(dto.enabledEvents());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize enabled events", e);
        }

        NotificationConfigEntity entity = new NotificationConfigEntity(
                userId,
                dto.provider(),
                dto.webhookUrl(),
                enabledEventsJson
        );

        entity = notificationConfigRepository.save(entity);
        return toDto(entity);
    }

    @Transactional
    public NotificationConfigDto updateNotificationConfig(UUID configId, UUID userId, UpdateNotificationConfigDto dto) {
        NotificationConfigEntity entity = notificationConfigRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("Notification config not found"));

        if (!entity.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        if (dto.webhookUrl() != null) {
            entity.setWebhookUrl(dto.webhookUrl());
        }

        if (dto.enabledEvents() != null) {
            try {
                String enabledEventsJson = objectMapper.writeValueAsString(dto.enabledEvents());
                entity.setEnabledEvents(enabledEventsJson);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize enabled events", e);
            }
        }

        if (dto.enabled() != null) {
            entity.setEnabled(dto.enabled());
        }

        entity = notificationConfigRepository.save(entity);
        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<NotificationConfigDto> getNotificationConfigs(UUID userId) {
        return notificationConfigRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteNotificationConfig(UUID configId, UUID userId) {
        NotificationConfigEntity entity = notificationConfigRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("Notification config not found"));

        if (!entity.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        notificationConfigRepository.delete(entity);
    }

    @Async
    public void sendRunStartedNotification(UUID userId, RunEntity run) {
        sendNotification(userId, "run_started", createRunStartedPayload(run));
    }

    @Async
    public void sendRunCompletedNotification(UUID userId, RunEntity run) {
        sendNotification(userId, "run_completed", createRunCompletedPayload(run));
    }

    @Async
    public void sendRunEscalatedNotification(UUID userId, RunEntity run) {
        sendNotification(userId, "run_escalated", createRunEscalatedPayload(run));
    }

    @Async
    public void sendCiCheckFailureNotification(UUID userId, RunEntity run, String logs) {
        sendNotification(userId, "ci_check_failure", createCiCheckFailurePayload(run, logs));
    }

    @Async
    public void sendMfaSetupReminderNotification(UUID userId, String username) {
        sendNotification(userId, "mfa_setup_reminder", createMfaSetupReminderPayload(username));
    }

    @Scheduled(cron = "0 0 9 * * *")
    public void sendDailyAnalyticsSummary() {
        log.info("Sending daily analytics summary notifications");
        
        List<NotificationConfigEntity> configs = notificationConfigRepository.findAll().stream()
                .filter(c -> c.isEnabled() && isEventEnabled(c, "daily_analytics"))
                .collect(Collectors.toList());

        for (NotificationConfigEntity config : configs) {
            try {
                String payload = createDailyAnalyticsSummaryPayload();
                deliverWebhook(config, "daily_analytics", payload);
            } catch (Exception e) {
                log.error("Failed to send daily analytics summary to user {}", config.getUserId(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 9 * * MON")
    public void sendWeeklyAnalyticsSummary() {
        log.info("Sending weekly analytics summary notifications");
        
        List<NotificationConfigEntity> configs = notificationConfigRepository.findAll().stream()
                .filter(c -> c.isEnabled() && isEventEnabled(c, "weekly_analytics"))
                .collect(Collectors.toList());

        for (NotificationConfigEntity config : configs) {
            try {
                String payload = createWeeklyAnalyticsSummaryPayload();
                deliverWebhook(config, "weekly_analytics", payload);
            } catch (Exception e) {
                log.error("Failed to send weekly analytics summary to user {}", config.getUserId(), e);
            }
        }
    }

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void retryFailedNotifications() {
        List<NotificationDeliveryLogEntity> failedLogs = notificationDeliveryLogRepository
                .findByStatusAndRetryCountLessThan("FAILED", MAX_RETRY_ATTEMPTS);

        for (NotificationDeliveryLogEntity log : failedLogs) {
            try {
                NotificationConfigEntity config = notificationConfigRepository
                        .findById(log.getNotificationConfigId())
                        .orElse(null);

                if (config != null && config.isEnabled()) {
                    log.incrementRetryCount();
                    deliverWebhookWithLog(config, log.getEventType(), log.getPayload(), log);
                }
            } catch (Exception e) {
                this.log.error("Failed to retry notification {}", log.getId(), e);
            }
        }
    }

    private void sendNotification(UUID userId, String eventType, String payload) {
        List<NotificationConfigEntity> configs = notificationConfigRepository
                .findByUserIdAndEnabled(userId, true);

        for (NotificationConfigEntity config : configs) {
            if (isEventEnabled(config, eventType)) {
                deliverWebhook(config, eventType, payload);
            }
        }
    }

    private void deliverWebhook(NotificationConfigEntity config, String eventType, String payload) {
        NotificationDeliveryLogEntity logEntity = new NotificationDeliveryLogEntity(
                config.getId(),
                eventType,
                payload,
                config.getWebhookUrl(),
                "PENDING"
        );
        logEntity = notificationDeliveryLogRepository.save(logEntity);
        deliverWebhookWithLog(config, eventType, payload, logEntity);
    }

    private void deliverWebhookWithLog(NotificationConfigEntity config, String eventType, 
                                       String payload, NotificationDeliveryLogEntity logEntity) {
        String webhookPayload = formatWebhookPayload(config.getProvider(), payload);

        webClient.post()
                .uri(config.getWebhookUrl())
                .header("Content-Type", "application/json")
                .bodyValue(webhookPayload)
                .retrieve()
                .toBodilessEntity()
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(5)))
                .timeout(Duration.ofSeconds(10))
                .subscribe(
                        response -> {
                            logEntity.setStatus("SUCCESS");
                            logEntity.setHttpStatusCode(response.getStatusCode().value());
                            logEntity.setDeliveredAt(Instant.now());
                            notificationDeliveryLogRepository.save(logEntity);
                            log.info("Successfully delivered {} notification to {}", eventType, config.getWebhookUrl());
                        },
                        error -> {
                            logEntity.setStatus("FAILED");
                            logEntity.setErrorMessage(error.getMessage());
                            notificationDeliveryLogRepository.save(logEntity);
                            log.error("Failed to deliver {} notification to {}", eventType, config.getWebhookUrl(), error);
                        }
                );
    }

    private String formatWebhookPayload(String provider, String payload) {
        try {
            JsonNode payloadNode = objectMapper.readTree(payload);
            
            if ("slack".equals(provider)) {
                Map<String, Object> slackPayload = new HashMap<>();
                slackPayload.put("text", payloadNode.get("text").asText());
                if (payloadNode.has("blocks")) {
                    slackPayload.put("blocks", payloadNode.get("blocks"));
                }
                return objectMapper.writeValueAsString(slackPayload);
            } else if ("discord".equals(provider)) {
                Map<String, Object> discordPayload = new HashMap<>();
                discordPayload.put("content", payloadNode.get("text").asText());
                if (payloadNode.has("embeds")) {
                    discordPayload.put("embeds", payloadNode.get("embeds"));
                }
                return objectMapper.writeValueAsString(discordPayload);
            }
            
            return payload;
        } catch (Exception e) {
            log.error("Failed to format webhook payload", e);
            return payload;
        }
    }

    private boolean isEventEnabled(NotificationConfigEntity config, String eventType) {
        try {
            List<String> enabledEvents = objectMapper.readValue(
                    config.getEnabledEvents(),
                    new TypeReference<List<String>>() {}
            );
            return enabledEvents.contains(eventType);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse enabled events", e);
            return false;
        }
    }

    private String createRunStartedPayload(RunEntity run) {
        String markdown = String.format(
                "### \uD83D\uDE80 Workflow Run Started\n\n" +
                "**Repository:** %s\n" +
                "**Issue:** #%d\n" +
                "**Mode:** %s\n" +
                "**Status:** %s\n" +
                "**Started:** %s\n",
                run.getRepo(),
                run.getIssueNumber(),
                run.getMode(),
                run.getStatus().name(),
                run.getCreatedAt().toString()
        );

        return createPayload("Workflow Run Started", markdown);
    }

    private String createRunCompletedPayload(RunEntity run) {
        String markdown = String.format(
                "### \u2705 Workflow Run Completed\n\n" +
                "**Repository:** %s\n" +
                "**Issue:** #%d\n" +
                "**Status:** %s\n" +
                "**CI Fixes:** %d\n" +
                "**E2E Fixes:** %d\n" +
                "**Duration:** %s\n",
                run.getRepo(),
                run.getIssueNumber(),
                run.getStatus().name(),
                run.getCiFixCount(),
                run.getE2eFixCount(),
                formatDuration(run.getCreatedAt(), run.getUpdatedAt())
        );

        String findings = getReviewFindings(run);
        if (!findings.isEmpty()) {
            markdown += "\n" + findings;
        }

        return createPayload("Workflow Run Completed", markdown);
    }

    private String createRunEscalatedPayload(RunEntity run) {
        String markdown = String.format(
                "### \u26A0\uFE0F Workflow Run Escalated\n\n" +
                "**Repository:** %s\n" +
                "**Issue:** #%d\n" +
                "**Status:** ESCALATED\n" +
                "**CI Fixes Attempted:** %d\n" +
                "**E2E Fixes Attempted:** %d\n" +
                "**Escalation Reason:** Manual review required\n",
                run.getRepo(),
                run.getIssueNumber(),
                run.getCiFixCount(),
                run.getE2eFixCount()
        );

        String findings = getReviewFindings(run);
        if (!findings.isEmpty()) {
            markdown += "\n" + findings;
        }

        return createPayload("Workflow Run Escalated", markdown);
    }

    private String createCiCheckFailurePayload(RunEntity run, String logs) {
        String truncatedLogs = logs.length() > 500 ? logs.substring(0, 500) + "..." : logs;
        
        String markdown = String.format(
                "### \u274C CI Check Failed\n\n" +
                "**Repository:** %s\n" +
                "**Issue:** #%d\n" +
                "**Fix Attempt:** %d\n\n" +
                "**Logs:**\n```\n%s\n```\n",
                run.getRepo(),
                run.getIssueNumber(),
                run.getCiFixCount(),
                truncatedLogs
        );

        return createPayload("CI Check Failed", markdown);
    }

    private String createMfaSetupReminderPayload(String username) {
        String markdown = String.format(
                "### \uD83D\uDD10 MFA Setup Reminder\n\n" +
                "Hi **%s**,\n\n" +
                "We noticed you haven't set up Multi-Factor Authentication (MFA) for your account yet. " +
                "MFA adds an extra layer of security to protect your account.\n\n" +
                "Please visit your account settings to enable MFA.\n",
                username
        );

        return createPayload("MFA Setup Reminder", markdown);
    }

    private String createDailyAnalyticsSummaryPayload() {
        Instant endDate = Instant.now();
        Instant startDate = endDate.minus(1, ChronoUnit.DAYS);
        
        RunsSummaryDto runStats = analyticsService.getRunsSummary(startDate, endDate);
        
        String markdown = String.format(
                "### \uD83D\uDCCA Daily Analytics Summary\n\n" +
                "**Period:** Last 24 hours\n\n" +
                "**Total Runs:** %d\n" +
                "**Success Rate:** %.1f%%\n" +
                "**Failure Rate:** %.1f%%\n" +
                "**Escalation Rate:** %.1f%%\n\n" +
                "**Status Breakdown:**\n",
                runStats.totalRuns(),
                runStats.successRate() * 100,
                runStats.failureRate() * 100,
                runStats.escalationRate() * 100
        );

        for (Map.Entry<String, Long> entry : runStats.statusBreakdown().entrySet()) {
            markdown += String.format("- %s: %d\n", entry.getKey(), entry.getValue());
        }

        return createPayload("Daily Analytics Summary", markdown);
    }

    private String createWeeklyAnalyticsSummaryPayload() {
        Instant endDate = Instant.now();
        Instant startDate = endDate.minus(7, ChronoUnit.DAYS);
        
        RunsSummaryDto runStats = analyticsService.getRunsSummary(startDate, endDate);
        AgentsPerformanceDto agentPerf = analyticsService.getAgentsPerformance(startDate, endDate);
        PersonasFindingsDto personaFindings = analyticsService.getPersonasFindings(startDate, endDate);
        
        String markdown = String.format(
                "### \uD83D\uDCC8 Weekly Analytics Summary\n\n" +
                "**Period:** Last 7 days\n\n" +
                "**Workflow Runs:**\n" +
                "- Total: %d\n" +
                "- Success Rate: %.1f%%\n" +
                "- Escalation Rate: %.1f%%\n\n" +
                "**Review Findings:**\n" +
                "- Total Findings: %d\n" +
                "- Mandatory: %d\n\n" +
                "**Agent Performance:**\n" +
                "- Avg Duration: %.1fs\n" +
                "- Error Rate: %.1f%%\n",
                runStats.totalRuns(),
                runStats.successRate() * 100,
                runStats.escalationRate() * 100,
                personaFindings.totalFindings(),
                personaFindings.mandatoryFindings(),
                agentPerf.overallAverageDuration(),
                agentPerf.overallErrorRate() * 100
        );

        return createPayload("Weekly Analytics Summary", markdown);
    }

    private String getReviewFindings(RunEntity run) {
        var personaArtifacts = runArtifactRepository.findByRunAndArtifactType(run, "persona_review.json");
        
        if (personaArtifacts.isEmpty()) {
            return "";
        }

        StringBuilder findings = new StringBuilder("**Review Findings:**\n");
        
        for (var artifact : personaArtifacts) {
            try {
                JsonNode payload = objectMapper.readTree(artifact.getPayload());
                JsonNode findingsNode = payload.get("findings");
                
                if (findingsNode != null && findingsNode.isArray()) {
                    for (JsonNode finding : findingsNode) {
                        JsonNode issues = finding.get("issues");
                        if (issues != null && issues.isArray()) {
                            for (JsonNode issue : issues) {
                                String severity = issue.has("severity") ? issue.get("severity").asText() : "unknown";
                                String description = issue.has("description") ? issue.get("description").asText() : "";
                                String location = issue.has("location") ? issue.get("location").asText() : "";
                                
                                String icon = getSeverityIcon(severity);
                                findings.append(String.format("\n%s **%s**: %s", icon, severity.toUpperCase(), description));
                                
                                if (!location.isEmpty()) {
                                    findings.append(String.format("\n  Location: `%s`", location));
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to parse persona review artifact", e);
            }
        }
        
        return findings.toString();
    }

    private String getSeverityIcon(String severity) {
        return switch (severity.toLowerCase()) {
            case "critical" -> "\uD83D\uDD34";
            case "high" -> "\uD83D\uDFE0";
            case "medium" -> "\uD83D\uDFE1";
            case "low" -> "\uD83D\uDFE2";
            default -> "\u26AA";
        };
    }

    private String formatDuration(Instant start, Instant end) {
        long seconds = Duration.between(start, end).getSeconds();
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private String createPayload(String title, String markdown) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", title);
        payload.put("markdown", markdown);
        
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to create notification payload", e);
            return "{}";
        }
    }

    private NotificationConfigDto toDto(NotificationConfigEntity entity) {
        List<String> enabledEvents;
        try {
            enabledEvents = objectMapper.readValue(
                    entity.getEnabledEvents(),
                    new TypeReference<List<String>>() {}
            );
        } catch (JsonProcessingException e) {
            enabledEvents = List.of();
        }

        return new NotificationConfigDto(
                entity.getId(),
                entity.getUserId(),
                entity.getProvider(),
                entity.getWebhookUrl(),
                enabledEvents,
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
