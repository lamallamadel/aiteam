package com.atlasia.ai.controller;

import com.atlasia.ai.config.OrchestratorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultHealth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final VaultTemplate vaultTemplate;
    private final OrchestratorProperties properties;
    private final WebClient webClient;

    public HealthController(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<VaultTemplate> vaultTemplateProvider,
            OrchestratorProperties properties,
            WebClient.Builder webClientBuilder) {
        this.jdbcTemplate = jdbcTemplate;
        this.vaultTemplate = vaultTemplateProvider.getIfAvailable();
        this.properties = properties;
        this.webClient = webClientBuilder.build();
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> checks = new HashMap<>();
        boolean allHealthy = true;
        Instant startTime = Instant.now();

        try {
            // Check 1: Database connectivity
            Map<String, Object> dbCheck = checkDatabase();
            checks.put("database", dbCheck);
            if (!"UP".equals(dbCheck.get("status"))) {
                allHealthy = false;
            }

            // Check 2: Vault availability
            Map<String, Object> vaultCheck = checkVault();
            checks.put("vault", vaultCheck);
            if (!"UP".equals(vaultCheck.get("status"))) {
                allHealthy = false;
            }

            // Check 3: LLM API reachability
            Map<String, Object> llmCheck = checkLlmApi();
            checks.put("llm", llmCheck);
            if (!"UP".equals(llmCheck.get("status"))) {
                allHealthy = false;
            }

            // Build response
            response.put("status", allHealthy ? "UP" : "DOWN");
            response.put("checks", checks);
            response.put("timestamp", Instant.now().toString());
            response.put("responseDuration", Duration.between(startTime, Instant.now()).toMillis() + "ms");

            // Return appropriate HTTP status
            HttpStatus status = allHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            
            if (!allHealthy) {
                log.warn("Readiness check failed: {}", response);
            }

            return ResponseEntity.status(status).body(response);

        } catch (Exception e) {
            log.error("Readiness check encountered unexpected error", e);
            response.put("status", "DOWN");
            response.put("error", e.getMessage());
            response.put("timestamp", Instant.now().toString());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }

    private Map<String, Object> checkDatabase() {
        Map<String, Object> result = new HashMap<>();
        Instant start = Instant.now();
        
        try {
            // Execute simple query to verify connectivity
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            
            if (value != null && value == 1) {
                result.put("status", "UP");
                result.put("message", "Database is accessible");
                
                // Get additional database metrics
                try {
                    Integer connectionCount = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()",
                        Integer.class
                    );
                    result.put("activeConnections", connectionCount);
                } catch (Exception e) {
                    log.debug("Could not fetch connection count: {}", e.getMessage());
                }
                
            } else {
                result.put("status", "DOWN");
                result.put("message", "Database query returned unexpected result");
            }
            
        } catch (Exception e) {
            result.put("status", "DOWN");
            result.put("message", "Database connection failed");
            result.put("error", e.getMessage());
            log.error("Database health check failed", e);
        }
        
        result.put("responseTime", Duration.between(start, Instant.now()).toMillis() + "ms");
        return result;
    }

    private Map<String, Object> checkVault() {
        Map<String, Object> result = new HashMap<>();
        Instant start = Instant.now();
        
        try {
            // Check if Vault is available/enabled
            if (vaultTemplate == null) {
                result.put("status", "UP");
                result.put("message", "Vault is not enabled or available in current environment");
                result.put("enabled", false);
                return result;
            }

            // Query Vault health
            VaultHealth vaultHealth = vaultTemplate.opsForSys().health();
            
            if (vaultHealth.isInitialized() && !vaultHealth.isSealed()) {
                result.put("status", "UP");
                result.put("message", "Vault is accessible and unsealed");
                result.put("initialized", vaultHealth.isInitialized());
                result.put("sealed", vaultHealth.isSealed());
                result.put("standby", vaultHealth.isStandby());
                result.put("version", vaultHealth.getVersion());
            } else if (vaultHealth.isSealed()) {
                result.put("status", "DOWN");
                result.put("message", "Vault is sealed");
                result.put("initialized", vaultHealth.isInitialized());
                result.put("sealed", true);
            } else {
                result.put("status", "DOWN");
                result.put("message", "Vault is not initialized");
                result.put("initialized", false);
            }
            
        } catch (Exception e) {
            result.put("status", "DOWN");
            result.put("message", "Vault connection failed");
            result.put("error", e.getMessage());
            log.error("Vault health check failed", e);
        }
        
        result.put("responseTime", Duration.between(start, Instant.now()).toMillis() + "ms");
        return result;
    }

    private Map<String, Object> checkLlmApi() {
        Map<String, Object> result = new HashMap<>();
        Instant start = Instant.now();
        
        try {
            String llmEndpoint = properties.llm().endpoint();
            String llmApiKey = properties.llm().apiKey();
            
            if (llmApiKey == null || llmApiKey.isEmpty() || "changeme".equals(llmApiKey)) {
                result.put("status", "UP");
                result.put("message", "LLM API key not configured (development mode)");
                result.put("configured", false);
                return result;
            }

            // Perform HEAD request to LLM endpoint to verify reachability
            // Using a simple models endpoint which is typically cheaper/faster
            String modelsEndpoint = llmEndpoint + "/models";
            
            try {
                Boolean isReachable = webClient.get()
                    .uri(modelsEndpoint)
                    .header("Authorization", "Bearer " + llmApiKey)
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful() || response.statusCode().is4xxClientError()) {
                            // Both 2xx and 4xx indicate the API is reachable
                            // 4xx might be due to model endpoint not existing, but API is up
                            return Mono.just(true);
                        }
                        return Mono.just(false);
                    })
                    .timeout(Duration.ofSeconds(5))
                    .onErrorReturn(false)
                    .block();

                if (Boolean.TRUE.equals(isReachable)) {
                    result.put("status", "UP");
                    result.put("message", "LLM API is reachable");
                    result.put("endpoint", llmEndpoint);
                    result.put("model", properties.llm().model());
                } else {
                    result.put("status", "DOWN");
                    result.put("message", "LLM API is not reachable");
                    result.put("endpoint", llmEndpoint);
                }
                
            } catch (Exception e) {
                result.put("status", "DOWN");
                result.put("message", "LLM API connection failed");
                result.put("endpoint", llmEndpoint);
                result.put("error", e.getMessage());
                log.error("LLM API health check failed", e);
            }
            
        } catch (Exception e) {
            result.put("status", "DOWN");
            result.put("message", "LLM API check encountered error");
            result.put("error", e.getMessage());
            log.error("LLM API health check setup failed", e);
        }
        
        result.put("responseTime", Duration.between(start, Instant.now()).toMillis() + "ms");
        return result;
    }
}
