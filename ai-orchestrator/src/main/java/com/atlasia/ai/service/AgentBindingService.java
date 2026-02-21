package com.atlasia.ai.service;

import com.atlasia.ai.config.OrchestratorProperties;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.service.A2ADiscoveryService.AgentCard;
import com.atlasia.ai.service.A2ADiscoveryService.AgentConstraints;
import com.atlasia.ai.service.exception.AgentStepException;
import com.atlasia.ai.service.exception.OrchestratorException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ACNBP Agent Binding Service — Capability, Negotiation, and Binding Protocol.
 *
 * Before each agent delegation, the orchestrator:
 *   1. Screens candidates via the A2A registry (screenCandidates).
 *   2. Negotiates a binding that declares the agent's role, capabilities,
 *      constraints, and duration (negotiate).
 *   3. Signs the binding with HMAC-SHA256 keyed by the orchestrator token
 *      to prevent tampered credentials (verifyBinding).
 *   4. Revokes the binding after the step completes (revokeBinding),
 *      or leaves it in the map for audit if the step fails (TTL expiry).
 *
 * All active bindings are queryable via GET /api/a2a/bindings for oversight.
 */
@Service
public class AgentBindingService {
    private static final Logger log = LoggerFactory.getLogger(AgentBindingService.class);

    private static final long DEFAULT_STEP_DURATION_MS = 300_000L; // 5 minutes
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final A2ADiscoveryService a2aDiscoveryService;
    private final BlackboardService blackboardService;
    private final OrchestratorProperties props;
    private final ObjectMapper objectMapper;

    /** Active bindings: bindingId → AgentBinding. */
    private final ConcurrentHashMap<UUID, AgentBinding> activeBindings = new ConcurrentHashMap<>();

    public AgentBindingService(
            A2ADiscoveryService a2aDiscoveryService,
            BlackboardService blackboardService,
            OrchestratorProperties props,
            ObjectMapper objectMapper) {
        this.a2aDiscoveryService = a2aDiscoveryService;
        this.blackboardService = blackboardService;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * Screen candidate agents for a role, validating capabilities against requirements.
     *
     * @param role     pipeline role
     * @param required required capabilities
     * @param ctx      current run context (for logging)
     * @return best matching AgentCard
     * @throws AgentStepException if no suitable candidate exists
     */
    public AgentCard screenCandidates(String role, Set<String> required, RunContext ctx) {
        AgentCard card = a2aDiscoveryService.discoverForRole(role, required);
        if (card == null) {
            throw new AgentStepException(
                    "No agent available for role '" + role + "' with capabilities " + required,
                    role, "screen", OrchestratorException.RecoveryStrategy.FAIL_FAST);
        }
        Set<String> declared = card.capabilities();
        Set<String> missing = new java.util.HashSet<>(required);
        missing.removeAll(declared);
        if (!missing.isEmpty()) {
            log.warn("ACNBP: agent={} missing capabilities={} for role={}", card.name(), missing, role);
        }
        return card;
    }

    /**
     * Negotiate a cryptographically-bound delegation for an agent step.
     *
     * @param candidate  the agent card (may be null for local-only execution)
     * @param required   capabilities required for this step
     * @param runId      current run UUID
     * @return a signed AgentBinding; binding is stored in the active map
     */
    public AgentBinding negotiate(AgentCard candidate, Set<String> required, UUID runId) {
        UUID bindingId = UUID.randomUUID();
        Instant issuedAt = Instant.now();

        String agentName = candidate != null ? candidate.name() : "local";
        String role      = candidate != null ? candidate.role() : "UNKNOWN";
        Set<String> declaredCapabilities = candidate != null ? candidate.capabilities() : Set.of();
        AgentConstraints constraints = candidate != null ? candidate.constraints() : null;

        long durationMs = (constraints != null && constraints.maxDurationMs() > 0)
                ? constraints.maxDurationMs() : DEFAULT_STEP_DURATION_MS;
        Instant expiresAt = issuedAt.plusMillis(durationMs);

        String signature = sign(bindingId, runId, agentName, role, issuedAt, expiresAt);

        AgentBinding binding = new AgentBinding(
                bindingId, runId, agentName, role,
                declaredCapabilities, required,
                constraints, issuedAt, expiresAt, signature);

        activeBindings.put(bindingId, binding);

        log.info("ACNBP BIND: bindingId={}, agent={}, role={}, runId={}, expiresAt={}",
                bindingId, agentName, role, runId, expiresAt);

        return binding;
    }

    /**
     * Verify that a binding's HMAC signature is still valid and the binding has not expired.
     *
     * @param binding the binding to verify
     * @return true if signature is valid and binding has not expired
     */
    public boolean verifyBinding(AgentBinding binding) {
        if (binding == null) return false;
        if (Instant.now().isAfter(binding.expiresAt())) {
            log.debug("ACNBP VERIFY: binding={} expired at {}", binding.bindingId(), binding.expiresAt());
            return false;
        }
        String expected = sign(binding.bindingId(), binding.runId(), binding.agentName(),
                binding.role(), binding.issuedAt(), binding.expiresAt());
        boolean valid = expected.equals(binding.signature());
        if (!valid) {
            log.warn("ACNBP VERIFY: binding={} signature mismatch", binding.bindingId());
        }
        return valid;
    }

    /**
     * Revoke a binding after step completion.
     * Revoked bindings are removed from the active map.
     * Expired/failed bindings remain for TTL-based audit.
     *
     * @param bindingId binding to revoke
     * @param reason    reason string (e.g., "step_completed")
     */
    public void revokeBinding(UUID bindingId, String reason) {
        AgentBinding removed = activeBindings.remove(bindingId);
        if (removed != null) {
            log.info("ACNBP REVOKE: bindingId={}, agent={}, role={}, reason={}",
                    bindingId, removed.agentName(), removed.role(), reason);
        }
    }

    /**
     * Returns a snapshot of all currently active bindings.
     * Useful for oversight and audit via GET /api/a2a/bindings.
     */
    public Map<UUID, AgentBinding> getActiveBindings() {
        return java.util.Collections.unmodifiableMap(activeBindings);
    }

    // -------------------------------------------------------------------------
    // HMAC-SHA256 signing
    // -------------------------------------------------------------------------

    private String sign(UUID bindingId, UUID runId, String agentName, String role,
            Instant issuedAt, Instant expiresAt) {
        String data = bindingId + "|" + runId + "|" + agentName + "|" + role
                + "|" + issuedAt + "|" + expiresAt;
        try {
            String keyMaterial = (props.token() != null && !props.token().isBlank())
                    ? props.token() : "default-signing-key";
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(keyMaterial.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            log.error("ACNBP: HMAC signing failed for binding={}", bindingId, e);
            return "signing-error";
        }
    }

    // -------------------------------------------------------------------------
    // AgentBinding record
    // -------------------------------------------------------------------------

    /**
     * Immutable, cryptographically-signed record of an agent delegation.
     */
    public record AgentBinding(
            UUID bindingId,
            UUID runId,
            String agentName,
            String role,
            Set<String> declaredCapabilities,
            Set<String> requiredCapabilities,
            AgentConstraints constraints,
            Instant issuedAt,
            Instant expiresAt,
            String signature) {}
}
