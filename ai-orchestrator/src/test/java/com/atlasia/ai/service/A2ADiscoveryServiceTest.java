package com.atlasia.ai.service;

import com.atlasia.ai.service.A2ADiscoveryService.AgentCard;
import com.atlasia.ai.service.A2ADiscoveryService.AgentConstraints;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class A2ADiscoveryServiceTest {

    @Mock
    private OrchestratorMetrics metrics;

    private A2ADiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new A2ADiscoveryService(metrics);
        service.registerDefaultAgents();
    }

    @Test
    void listAllCapabilities_returnsAllUniqueCapabilities() {
        Set<String> capabilities = service.listAllCapabilities();

        assertThat(capabilities).isNotEmpty();
        assertThat(capabilities).contains(
                "ticket_analysis",
                "code_generation",
                "code_review"
        );
    }

    @Test
    void register_addsAgentAndCapabilitiesToIndex() {
        AgentConstraints constraints = new AgentConstraints(4096, 60000, 0.05);
        AgentCard customAgent = new AgentCard(
                "custom-analyzer", "1.0", "ANALYZER", "third-party",
                "Custom analysis agent",
                Set.of("custom_analysis", "data_processing"),
                "analysis_result", List.of(), constraints, "http", null, "active"
        );

        service.register(customAgent);

        Set<String> capabilities = service.listAllCapabilities();
        assertThat(capabilities).contains("custom_analysis", "data_processing");

        AgentCard retrieved = service.getAgent("custom-analyzer");
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.name()).isEqualTo("custom-analyzer");
    }

    @Test
    void deregister_removesAgentAndCleanupCapabilityIndex() {
        service.deregister("pm-v1");

        AgentCard removed = service.getAgent("pm-v1");
        assertThat(removed).isNull();
    }

    @Test
    void discover_findsAgentsByCapabilities() {
        List<AgentCard> matches = service.discover(Set.of("code_generation"));

        assertThat(matches).isNotEmpty();
        assertThat(matches).anyMatch(card -> card.capabilities().contains("code_generation"));
    }

    @Test
    void listAllAgents_returnsAllRegisteredAgents() {
        List<AgentCard> agents = service.listAllAgents();

        assertThat(agents).isNotEmpty();
        assertThat(agents.size()).isGreaterThanOrEqualTo(8);
    }
}
