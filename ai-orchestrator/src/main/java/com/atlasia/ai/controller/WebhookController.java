package com.atlasia.ai.controller;

import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.model.WebhookEventEntity;
import com.atlasia.ai.persistence.RunRepository;
import com.atlasia.ai.persistence.WebhookEventRepository;
import com.atlasia.ai.service.WorkflowEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final WebhookEventRepository webhookEventRepository;
    private final RunRepository runRepository;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

    @Value("${atlasia.webhook.github.secret:}")
    private String githubWebhookSecret;

    public WebhookController(
            WebhookEventRepository webhookEventRepository,
            RunRepository runRepository,
            WorkflowEngine workflowEngine,
            ObjectMapper objectMapper) {
        this.webhookEventRepository = webhookEventRepository;
        this.runRepository = runRepository;
        this.workflowEngine = workflowEngine;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestBody String payload) {

        log.info("Received GitHub webhook: eventType={}, signaturePresent={}", eventType, signature != null);

        boolean signatureValid = verifySignature(payload, signature);
        
        WebhookEventEntity webhookEvent = new WebhookEventEntity(
                eventType != null ? eventType : "unknown",
                payload,
                signatureValid,
                Instant.now());
        webhookEventRepository.save(webhookEvent);

        if (!signatureValid) {
            log.warn("Invalid webhook signature for event: {}", eventType);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\":\"Invalid signature\"}");
        }

        if (!"issues".equals(eventType)) {
            log.debug("Ignoring non-issues event: {}", eventType);
            return ResponseEntity.ok("{\"status\":\"ignored\",\"reason\":\"not an issues event\"}");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String action = root.path("action").asText();

            if (!"labeled".equals(action)) {
                log.debug("Ignoring issues event with action: {}", action);
                return ResponseEntity.ok("{\"status\":\"ignored\",\"reason\":\"not a labeled action\"}");
            }

            JsonNode label = root.path("label");
            String labelName = label.path("name").asText();

            if (!"ai:run".equals(labelName)) {
                log.debug("Ignoring labeled event with non-matching label: {}", labelName);
                return ResponseEntity.ok("{\"status\":\"ignored\",\"reason\":\"label is not ai:run\"}");
            }

            JsonNode issue = root.path("issue");
            JsonNode repository = root.path("repository");

            int issueNumber = issue.path("number").asInt();
            String issueTitle = issue.path("title").asText();
            String issueBody = issue.path("body").asText("");
            String repoFullName = repository.path("full_name").asText();

            log.info("Processing ai:run label event: repo={}, issue={}, title={}", 
                    repoFullName, issueNumber, issueTitle);

            UUID runId = UUID.randomUUID();
            RunEntity runEntity = new RunEntity(
                    runId,
                    repoFullName,
                    issueNumber,
                    "code",
                    RunStatus.RECEIVED,
                    Instant.now());

            runRepository.save(runEntity);

            String githubToken = extractGitHubToken(root);
            if (githubToken == null) {
                log.warn("No GitHub token available for webhook-triggered run: runId={}", runId);
                githubToken = "";
            }

            workflowEngine.executeWorkflowAsync(runId, githubToken);

            log.info("Started autonomous run from webhook: runId={}, repo={}, issue={}", 
                    runId, repoFullName, issueNumber);

            return ResponseEntity.ok(String.format(
                    "{\"status\":\"accepted\",\"runId\":\"%s\",\"repo\":\"%s\",\"issueNumber\":%d}",
                    runId, repoFullName, issueNumber));

        } catch (Exception e) {
            log.error("Failed to process GitHub webhook payload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"Failed to process webhook\"}");
        }
    }

    private boolean verifySignature(String payload, String signature) {
        if (signature == null || signature.isEmpty()) {
            log.warn("Missing X-Hub-Signature-256 header");
            return false;
        }

        if (githubWebhookSecret == null || githubWebhookSecret.isEmpty()) {
            log.warn("GitHub webhook secret not configured");
            return false;
        }

        if (!signature.startsWith("sha256=")) {
            log.warn("Invalid signature format: {}", signature);
            return false;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    githubWebhookSecret.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256);
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = "sha256=" + HexFormat.of().formatHex(hash);

            boolean valid = MessageDigestEquals(expectedSignature, signature);
            
            if (!valid) {
                log.warn("Signature mismatch: expected={}, received={}", expectedSignature, signature);
            }

            return valid;

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to verify webhook signature", e);
            return false;
        }
    }

    private boolean MessageDigestEquals(String expected, String actual) {
        if (expected.length() != actual.length()) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < expected.length(); i++) {
            result |= expected.charAt(i) ^ actual.charAt(i);
        }
        return result == 0;
    }

    private String extractGitHubToken(JsonNode root) {
        JsonNode installation = root.path("installation");
        if (!installation.isMissingNode()) {
            return null;
        }
        
        JsonNode sender = root.path("sender");
        if (!sender.isMissingNode()) {
            return null;
        }
        
        return null;
    }
}
