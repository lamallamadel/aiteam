package com.atlasia.ai.contract;

import com.atlasia.ai.service.A2ADiscoveryService.AgentCard;
import com.atlasia.ai.service.A2ADiscoveryService.AgentConstraints;
import com.atlasia.ai.service.AgentBindingService.AgentBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract Validation Test Suite using ContractValidationHelper.
 * 
 * Tests contract validation utilities and pattern matching for A2A protocol
 * data structures.
 */
@DisplayName("Contract Validation Helper Tests")
class ContractValidationTest {

    @Test
    @DisplayName("UUID pattern validation")
    void testUUIDPatternValidation() {
        String validUUID = "550e8400-e29b-41d4-a716-446655440000";
        String invalidUUID = "not-a-uuid";
        String malformedUUID = "550e8400-e29b-41d4-a716";

        assertThat(ContractValidationHelper.isValidUUID(validUUID)).isTrue();
        assertThat(ContractValidationHelper.isValidUUID(invalidUUID)).isFalse();
        assertThat(ContractValidationHelper.isValidUUID(malformedUUID)).isFalse();
    }

    @Test
    @DisplayName("Version pattern validation")
    void testVersionPatternValidation() {
        assertThat(ContractValidationHelper.isValidVersion("1.0")).isTrue();
        assertThat(ContractValidationHelper.isValidVersion("2.1.3")).isTrue();
        assertThat(ContractValidationHelper.isValidVersion("10.20.30")).isTrue();
        assertThat(ContractValidationHelper.isValidVersion("v1.0")).isFalse();
        assertThat(ContractValidationHelper.isValidVersion("1.0.0-beta")).isFalse();
    }

    @Test
    @DisplayName("Role pattern validation")
    void testRolePatternValidation() {
        assertThat(ContractValidationHelper.isValidRole("DEVELOPER")).isTrue();
        assertThat(ContractValidationHelper.isValidRole("PM")).isTrue();
        assertThat(ContractValidationHelper.isValidRole("CODE_REVIEWER")).isTrue();
        assertThat(ContractValidationHelper.isValidRole("developer")).isFalse();
        assertThat(ContractValidationHelper.isValidRole("Developer-123")).isFalse();
    }

    @Test
    @DisplayName("Status pattern validation")
    void testStatusPatternValidation() {
        assertThat(ContractValidationHelper.isValidStatus("active")).isTrue();
        assertThat(ContractValidationHelper.isValidStatus("degraded")).isTrue();
        assertThat(ContractValidationHelper.isValidStatus("inactive")).isTrue();
        assertThat(ContractValidationHelper.isValidStatus("ACTIVE")).isFalse();
        assertThat(ContractValidationHelper.isValidStatus("unknown")).isFalse();
    }

    @Test
    @DisplayName("Repository pattern validation")
    void testRepositoryPatternValidation() {
        assertThat(ContractValidationHelper.isValidRepo("owner/repo")).isTrue();
        assertThat(ContractValidationHelper.isValidRepo("my-org/my-repo-123")).isTrue();
        assertThat(ContractValidationHelper.isValidRepo("owner_name/repo_name")).isTrue();
        assertThat(ContractValidationHelper.isValidRepo("invalidrepo")).isFalse();
        assertThat(ContractValidationHelper.isValidRepo("owner/repo/extra")).isFalse();
    }

    @Test
    @DisplayName("ISO-8601 timestamp pattern validation")
    void testISO8601PatternValidation() {
        assertThat(ContractValidationHelper.isValidISO8601("2024-01-15T10:30:00Z")).isTrue();
        assertThat(ContractValidationHelper.isValidISO8601("2024-01-15T10:30:00.123Z")).isTrue();
        assertThat(ContractValidationHelper.isValidISO8601("2024-01-15T10:30:00")).isTrue();
        assertThat(ContractValidationHelper.isValidISO8601("2024-01-15")).isFalse();
        assertThat(ContractValidationHelper.isValidISO8601("invalid-date")).isFalse();
    }

    @Test
    @DisplayName("Agent name pattern validation")
    void testAgentNamePatternValidation() {
        assertThat(ContractValidationHelper.isValidAgentName("developer-v1")).isTrue();
        assertThat(ContractValidationHelper.isValidAgentName("pm_agent_123")).isTrue();
        assertThat(ContractValidationHelper.isValidAgentName("TestAgent")).isTrue();
        assertThat(ContractValidationHelper.isValidAgentName("agent@email")).isFalse();
        assertThat(ContractValidationHelper.isValidAgentName("agent name")).isFalse();
    }

    @Test
    @DisplayName("AgentCard structure validation")
    void testAgentCardStructureValidation() {
        AgentConstraints validConstraints = new AgentConstraints(8192, 300_000, 0.10);
        
        AgentCard validCard = new AgentCard(
            "developer-v1",
            "1.0",
            "DEVELOPER",
            "atlasia",
            "Developer agent",
            Set.of("code_generation", "security_review"),
            "code_changes",
            List.of(),
            validConstraints,
            "local",
            null,
            "active"
        );

        assertThat(ContractValidationHelper.validateAgentCardStructure(validCard)).isTrue();

        AgentCard invalidNameCard = new AgentCard(
            "invalid name",
            "1.0",
            "DEVELOPER",
            "atlasia",
            "Developer agent",
            Set.of("code_generation"),
            "code_changes",
            List.of(),
            validConstraints,
            "local",
            null,
            "active"
        );
        assertThat(ContractValidationHelper.validateAgentCardStructure(invalidNameCard)).isFalse();

        AgentCard invalidStatusCard = new AgentCard(
            "developer-v1",
            "1.0",
            "DEVELOPER",
            "atlasia",
            "Developer agent",
            Set.of("code_generation"),
            "code_changes",
            List.of(),
            validConstraints,
            "local",
            null,
            "UNKNOWN"
        );
        assertThat(ContractValidationHelper.validateAgentCardStructure(invalidStatusCard)).isFalse();
    }

    @Test
    @DisplayName("AgentConstraints validation")
    void testConstraintsValidation() {
        AgentConstraints validConstraints = new AgentConstraints(8192, 300_000, 0.10);
        assertThat(ContractValidationHelper.validateConstraints(validConstraints)).isTrue();

        AgentConstraints negativeTokens = new AgentConstraints(-100, 300_000, 0.10);
        assertThat(ContractValidationHelper.validateConstraints(negativeTokens)).isFalse();

        AgentConstraints zeroDuration = new AgentConstraints(8192, 0, 0.10);
        assertThat(ContractValidationHelper.validateConstraints(zeroDuration)).isFalse();

        AgentConstraints negativeCost = new AgentConstraints(8192, 300_000, -0.10);
        assertThat(ContractValidationHelper.validateConstraints(negativeCost)).isFalse();
    }

    @Test
    @DisplayName("AgentBinding structure validation")
    void testAgentBindingValidation() {
        UUID bindingId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AgentConstraints constraints = new AgentConstraints(8192, 300_000, 0.10);
        Instant now = Instant.now();

        AgentBinding validBinding = new AgentBinding(
            bindingId,
            runId,
            "developer-v1",
            "DEVELOPER",
            Set.of("code_generation", "security_review"),
            Set.of("code_generation"),
            constraints,
            now,
            now.plusSeconds(300),
            "valid-signature"
        );

        assertThat(ContractValidationHelper.validateAgentBinding(validBinding)).isTrue();

        AgentBinding expiredBinding = new AgentBinding(
            bindingId,
            runId,
            "developer-v1",
            "DEVELOPER",
            Set.of("code_generation"),
            Set.of("code_generation"),
            constraints,
            now.minusSeconds(600),
            now.minusSeconds(300),
            "valid-signature"
        );
        assertThat(ContractValidationHelper.validateAgentBinding(expiredBinding)).isTrue();
        assertThat(ContractValidationHelper.isBindingExpired(expiredBinding)).isTrue();
        assertThat(ContractValidationHelper.isBindingValid(expiredBinding)).isFalse();

        AgentBinding invalidExpiryBinding = new AgentBinding(
            bindingId,
            runId,
            "developer-v1",
            "DEVELOPER",
            Set.of("code_generation"),
            Set.of("code_generation"),
            constraints,
            now,
            now.minusSeconds(100),
            "valid-signature"
        );
        assertThat(ContractValidationHelper.validateAgentBinding(invalidExpiryBinding)).isFalse();
    }

    @Test
    @DisplayName("Capability matching validation")
    void testCapabilityMatchValidation() {
        Set<String> declared = Set.of("code_generation", "security_review", "code_quality");
        Set<String> requiredFull = Set.of("code_generation", "security_review");
        Set<String> requiredPartial = Set.of("code_generation", "testing");

        assertThat(ContractValidationHelper.validateCapabilityMatch(declared, requiredFull)).isTrue();
        assertThat(ContractValidationHelper.validateCapabilityMatch(declared, requiredPartial)).isFalse();
    }

    @Test
    @DisplayName("Capability coverage calculation")
    void testCapabilityCoverageCalculation() {
        Set<String> declared = Set.of("code_generation", "security_review", "code_quality");
        
        Set<String> required100 = Set.of("code_generation", "security_review");
        assertThat(ContractValidationHelper.computeCapabilityCoverage(declared, required100))
            .isEqualTo(1.0);

        Set<String> required50 = Set.of("code_generation", "testing");
        assertThat(ContractValidationHelper.computeCapabilityCoverage(declared, required50))
            .isEqualTo(0.5);

        Set<String> required0 = Set.of("testing", "deployment");
        assertThat(ContractValidationHelper.computeCapabilityCoverage(declared, required0))
            .isEqualTo(0.0);

        Set<String> requiredEmpty = Set.of();
        assertThat(ContractValidationHelper.computeCapabilityCoverage(declared, requiredEmpty))
            .isEqualTo(1.0);
    }

    @Test
    @DisplayName("Binding expiry check")
    void testBindingExpiryCheck() {
        UUID bindingId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AgentConstraints constraints = new AgentConstraints(8192, 300_000, 0.10);
        Instant now = Instant.now();

        AgentBinding futureBinding = new AgentBinding(
            bindingId,
            runId,
            "developer-v1",
            "DEVELOPER",
            Set.of("code_generation"),
            Set.of("code_generation"),
            constraints,
            now,
            now.plusSeconds(300),
            "signature"
        );
        assertThat(ContractValidationHelper.isBindingExpired(futureBinding)).isFalse();
        assertThat(ContractValidationHelper.isBindingValid(futureBinding)).isTrue();

        AgentBinding pastBinding = new AgentBinding(
            bindingId,
            runId,
            "developer-v1",
            "DEVELOPER",
            Set.of("code_generation"),
            Set.of("code_generation"),
            constraints,
            now.minusSeconds(600),
            now.minusSeconds(100),
            "signature"
        );
        assertThat(ContractValidationHelper.isBindingExpired(pastBinding)).isTrue();
        assertThat(ContractValidationHelper.isBindingValid(pastBinding)).isFalse();
    }

    @Test
    @DisplayName("Pattern getter methods return valid regex patterns")
    void testPatternGetters() {
        assertThat(ContractValidationHelper.getUUIDPattern()).isNotBlank();
        assertThat(ContractValidationHelper.getVersionPattern()).isNotBlank();
        assertThat(ContractValidationHelper.getRolePattern()).isNotBlank();
        assertThat(ContractValidationHelper.getStatusPattern()).isNotBlank();
        assertThat(ContractValidationHelper.getRepoPattern()).isNotBlank();
        assertThat(ContractValidationHelper.getISO8601Pattern()).isNotBlank();
        assertThat(ContractValidationHelper.getAgentNamePattern()).isNotBlank();

        String testUUID = UUID.randomUUID().toString();
        assertThat(testUUID).matches(ContractValidationHelper.getUUIDPattern());
        
        assertThat("1.0.0").matches(ContractValidationHelper.getVersionPattern());
        assertThat("DEVELOPER").matches(ContractValidationHelper.getRolePattern());
        assertThat("active").matches(ContractValidationHelper.getStatusPattern());
        assertThat("owner/repo").matches(ContractValidationHelper.getRepoPattern());
        assertThat("developer-v1").matches(ContractValidationHelper.getAgentNamePattern());
    }
}
