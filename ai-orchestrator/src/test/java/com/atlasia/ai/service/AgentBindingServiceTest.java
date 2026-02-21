package com.atlasia.ai.service;

import com.atlasia.ai.config.OrchestratorProperties;
import com.atlasia.ai.service.A2ADiscoveryService.AgentCard;
import com.atlasia.ai.service.A2ADiscoveryService.AgentConstraints;
import com.atlasia.ai.service.AgentBindingService.AgentBinding;
import com.atlasia.ai.service.exception.AgentStepException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentBindingServiceTest {

    @Mock private A2ADiscoveryService a2aDiscoveryService;
    @Mock private BlackboardService blackboardService;
    @Mock private OrchestratorProperties props;

    private AgentBindingService service;

    @BeforeEach
    void setUp() {
        lenient().when(props.token()).thenReturn("test-signing-key");
        service = new AgentBindingService(a2aDiscoveryService, blackboardService, props, new ObjectMapper());
    }

    // -------------------------------------------------------------------------
    // negotiate
    // -------------------------------------------------------------------------

    @Test
    void negotiate_withAgentCard_createsSignedBinding() {
        AgentCard card = agentCard("pm-v1", "PM", Set.of("ticket_analysis"));
        UUID runId = UUID.randomUUID();

        AgentBinding binding = service.negotiate(card, Set.of("ticket_analysis"), runId);

        assertNotNull(binding);
        assertNotNull(binding.bindingId());
        assertEquals("pm-v1", binding.agentName());
        assertEquals("PM", binding.role());
        assertEquals(runId, binding.runId());
        assertNotNull(binding.signature());
        assertFalse(binding.signature().isEmpty());
        assertTrue(binding.expiresAt().isAfter(binding.issuedAt()));
    }

    @Test
    void negotiate_withNullCard_createsLocalBinding() {
        UUID runId = UUID.randomUUID();

        AgentBinding binding = service.negotiate(null, Set.of("ticket_analysis"), runId);

        assertEquals("local", binding.agentName());
        assertEquals("UNKNOWN", binding.role());
        assertTrue(binding.declaredCapabilities().isEmpty());
        assertNotNull(binding.bindingId());
    }

    @Test
    void negotiate_storesBindingInActiveMap() {
        AgentCard card = agentCard("pm-v1", "PM", Set.of("ticket_analysis"));
        UUID runId = UUID.randomUUID();

        AgentBinding binding = service.negotiate(card, Set.of("ticket_analysis"), runId);

        assertTrue(service.getActiveBindings().containsKey(binding.bindingId()));
        assertEquals(binding, service.getActiveBindings().get(binding.bindingId()));
    }

    @Test
    void negotiate_usesConstraintDurationForExpiry() {
        // 60-second constraint
        AgentConstraints constraints = new AgentConstraints(8192, 60_000L, 0.10);
        AgentCard card = new AgentCard("test-v1", "1.0", "TEST", "atlasia", "Test",
                Set.of("testing"), "output", List.of(), constraints, "local", null, "active");
        UUID runId = UUID.randomUUID();

        AgentBinding binding = service.negotiate(card, Set.of("testing"), runId);

        long durationMs = binding.expiresAt().toEpochMilli() - binding.issuedAt().toEpochMilli();
        assertEquals(60_000L, durationMs, 1_000L); // within 1s tolerance
    }

    @Test
    void negotiate_withZeroDurationConstraint_usesDefaultDuration() {
        // maxDurationMs = 0 → should fall back to 300_000 ms
        AgentConstraints constraints = new AgentConstraints(8192, 0, 0.10);
        AgentCard card = new AgentCard("test-v1", "1.0", "TEST", "atlasia", "Test",
                Set.of("testing"), "output", List.of(), constraints, "local", null, "active");

        AgentBinding binding = service.negotiate(card, Set.of("testing"), UUID.randomUUID());

        long durationMs = binding.expiresAt().toEpochMilli() - binding.issuedAt().toEpochMilli();
        assertEquals(300_000L, durationMs, 1_000L);
    }

    @Test
    void negotiate_multipleBindings_eachHasUniqueId() {
        AgentCard card = agentCard("pm-v1", "PM", Set.of("ticket_analysis"));

        AgentBinding b1 = service.negotiate(card, Set.of("ticket_analysis"), UUID.randomUUID());
        AgentBinding b2 = service.negotiate(card, Set.of("ticket_analysis"), UUID.randomUUID());

        assertNotEquals(b1.bindingId(), b2.bindingId());
        assertEquals(2, service.getActiveBindings().size());
    }

    // -------------------------------------------------------------------------
    // verifyBinding
    // -------------------------------------------------------------------------

    @Test
    void verifyBinding_freshBinding_returnsTrue() {
        AgentCard card = agentCard("pm-v1", "PM", Set.of("ticket_analysis"));
        AgentBinding binding = service.negotiate(card, Set.of("ticket_analysis"), UUID.randomUUID());

        assertTrue(service.verifyBinding(binding));
    }

    @Test
    void verifyBinding_nullBinding_returnsFalse() {
        assertFalse(service.verifyBinding(null));
    }

    @Test
    void verifyBinding_expiredBinding_returnsFalse() {
        Instant past = Instant.now().minusSeconds(60);
        AgentBinding expired = new AgentBinding(
                UUID.randomUUID(), UUID.randomUUID(), "pm-v1", "PM",
                Set.of(), Set.of(), null,
                past.minusSeconds(60), past, "some-signature");

        assertFalse(service.verifyBinding(expired));
    }

    @Test
    void verifyBinding_tamperedSignature_returnsFalse() {
        AgentCard card = agentCard("pm-v1", "PM", Set.of("ticket_analysis"));
        AgentBinding original = service.negotiate(card, Set.of("ticket_analysis"), UUID.randomUUID());

        AgentBinding tampered = new AgentBinding(
                original.bindingId(), original.runId(), original.agentName(), original.role(),
                original.declaredCapabilities(), original.requiredCapabilities(),
                original.constraints(), original.issuedAt(), original.expiresAt(),
                "tampered-invalid-signature");

        assertFalse(service.verifyBinding(tampered));
    }

    @Test
    void verifyBinding_differentKeyProducesDifferentSignature() {
        // Service with key "key-a"
        lenient().when(props.token()).thenReturn("key-a");
        AgentBindingService serviceA = new AgentBindingService(
                a2aDiscoveryService, blackboardService, props, new ObjectMapper());

        AgentCard card = agentCard("pm-v1", "PM", Set.of("ticket_analysis"));
        AgentBinding binding = serviceA.negotiate(card, Set.of("ticket_analysis"), UUID.randomUUID());

        // Service with key "key-b" cannot verify binding signed by key-a
        lenient().when(props.token()).thenReturn("key-b");
        AgentBindingService serviceB = new AgentBindingService(
                a2aDiscoveryService, blackboardService, props, new ObjectMapper());

        assertFalse(serviceB.verifyBinding(binding));
    }

    // -------------------------------------------------------------------------
    // revokeBinding
    // -------------------------------------------------------------------------

    @Test
    void revokeBinding_removesFromActiveMap() {
        AgentCard card = agentCard("pm-v1", "PM", Set.of("ticket_analysis"));
        AgentBinding binding = service.negotiate(card, Set.of("ticket_analysis"), UUID.randomUUID());
        UUID bindingId = binding.bindingId();

        assertTrue(service.getActiveBindings().containsKey(bindingId));

        service.revokeBinding(bindingId, "step_completed");

        assertFalse(service.getActiveBindings().containsKey(bindingId));
    }

    @Test
    void revokeBinding_nonExistentId_doesNotThrow() {
        assertDoesNotThrow(() -> service.revokeBinding(UUID.randomUUID(), "test"));
    }

    @Test
    void revokeBinding_onlyRemovesTargetBinding() {
        AgentCard card = agentCard("pm-v1", "PM", Set.of("ticket_analysis"));
        AgentBinding b1 = service.negotiate(card, Set.of("ticket_analysis"), UUID.randomUUID());
        AgentBinding b2 = service.negotiate(card, Set.of("ticket_analysis"), UUID.randomUUID());

        service.revokeBinding(b1.bindingId(), "step_completed");

        assertFalse(service.getActiveBindings().containsKey(b1.bindingId()));
        assertTrue(service.getActiveBindings().containsKey(b2.bindingId()));
        assertEquals(1, service.getActiveBindings().size());
    }

    // -------------------------------------------------------------------------
    // getActiveBindings
    // -------------------------------------------------------------------------

    @Test
    void getActiveBindings_emptyAtStart() {
        assertTrue(service.getActiveBindings().isEmpty());
    }

    @Test
    void getActiveBindings_returnsUnmodifiableView() {
        Map<UUID, AgentBinding> bindings = service.getActiveBindings();

        assertThrows(UnsupportedOperationException.class,
                () -> bindings.put(UUID.randomUUID(), null));
    }

    @Test
    void getActiveBindings_includesAllNegotiatedBindings() {
        AgentCard card = agentCard("pm-v1", "PM", Set.of("ticket_analysis"));
        service.negotiate(card, Set.of("ticket_analysis"), UUID.randomUUID());
        service.negotiate(card, Set.of("ticket_analysis"), UUID.randomUUID());
        service.negotiate(card, Set.of("ticket_analysis"), UUID.randomUUID());

        assertEquals(3, service.getActiveBindings().size());
    }

    // -------------------------------------------------------------------------
    // screenCandidates
    // -------------------------------------------------------------------------

    @Test
    void screenCandidates_agentFound_returnsCard() {
        AgentCard card = agentCard("pm-v1", "PM", Set.of("ticket_analysis", "requirement_extraction"));
        when(a2aDiscoveryService.discoverForRole(eq("PM"), any())).thenReturn(card);
        RunContext ctx = mock(RunContext.class);

        AgentCard result = service.screenCandidates("PM", Set.of("ticket_analysis"), ctx);

        assertEquals(card, result);
    }

    @Test
    void screenCandidates_noAgentFound_throwsAgentStepException() {
        when(a2aDiscoveryService.discoverForRole(any(), any())).thenReturn(null);
        RunContext ctx = mock(RunContext.class);

        assertThrows(AgentStepException.class,
                () -> service.screenCandidates("PM", Set.of("ticket_analysis"), ctx));
    }

    @Test
    void screenCandidates_missingCapabilities_logsWarningButReturnsCard() {
        // Card has ticket_analysis but not requirement_extraction
        AgentCard card = agentCard("pm-v1", "PM", Set.of("ticket_analysis"));
        when(a2aDiscoveryService.discoverForRole(eq("PM"), any())).thenReturn(card);
        RunContext ctx = mock(RunContext.class);

        // Should still return the card (only logs a warning for missing caps)
        AgentCard result = service.screenCandidates(
                "PM", Set.of("ticket_analysis", "requirement_extraction"), ctx);

        assertEquals(card, result);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AgentCard agentCard(String name, String role, Set<String> capabilities) {
        AgentConstraints constraints = new AgentConstraints(8192, 300_000, 0.10);
        return new AgentCard(name, "1.0", role, "atlasia", "Test agent",
                capabilities, "output", List.of(), constraints, "local", null, "active");
    }
}
