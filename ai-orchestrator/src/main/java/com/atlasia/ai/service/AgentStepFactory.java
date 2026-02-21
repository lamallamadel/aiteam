package com.atlasia.ai.service;

import com.atlasia.ai.service.A2ADiscoveryService.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * AgentStepFactory — A2A-aware router that maps pipeline roles to AgentStep beans.
 *
 * For each standard pipeline step (PM, QUALIFIER, ARCHITECT, TESTER, WRITER),
 * the factory:
 *   1. Queries the A2A registry for the best matching agent card.
 *   2. Logs which card was selected (for audit / future remote routing).
 *   3. Returns the local AgentStep bean (remote adapter is a future extension).
 *
 * DeveloperStep is NOT routed through this factory because its contract
 * includes methods beyond AgentStep.execute() (generateCode + commitAndCreatePullRequest).
 */
@Service
public class AgentStepFactory {
    private static final Logger log = LoggerFactory.getLogger(AgentStepFactory.class);

    private final PmStep pmStep;
    private final QualifierStep qualifierStep;
    private final ArchitectStep architectStep;
    private final TesterStep testerStep;
    private final WriterStep writerStep;
    private final A2ADiscoveryService a2aDiscoveryService;

    public AgentStepFactory(
            PmStep pmStep,
            QualifierStep qualifierStep,
            ArchitectStep architectStep,
            TesterStep testerStep,
            WriterStep writerStep,
            A2ADiscoveryService a2aDiscoveryService) {
        this.pmStep = pmStep;
        this.qualifierStep = qualifierStep;
        this.architectStep = architectStep;
        this.testerStep = testerStep;
        this.writerStep = writerStep;
        this.a2aDiscoveryService = a2aDiscoveryService;
    }

    /**
     * Resolve the AgentStep for a given role.
     *
     * Consults the A2A registry to determine the best card for the role,
     * logs the selection, then returns the corresponding local step bean.
     *
     * @param role                 pipeline role (PM, QUALIFIER, ARCHITECT, TESTER, WRITER)
     * @param requiredCapabilities capabilities needed for this execution
     * @return the resolved AgentStep bean
     * @throws IllegalArgumentException if the role is unknown
     */
    public AgentStep resolveForRole(String role, Set<String> requiredCapabilities) {
        AgentCard card = a2aDiscoveryService.discoverForRole(role, requiredCapabilities);
        if (card != null) {
            log.info("A2A ROUTE: selected agent={} for role={} capabilities={}",
                    card.name(), role, requiredCapabilities);
        } else {
            log.debug("A2A ROUTE: no card found for role={}, using local default", role);
        }
        return localStepFor(role);
    }

    /**
     * Convenience method — returns the active AgentCard for a role without
     * triggering step resolution.
     *
     * @param role pipeline role
     * @return best matching AgentCard from the registry, or null if none found
     */
    public AgentCard getActiveCard(String role) {
        return a2aDiscoveryService.discoverForRole(role, Set.of());
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private AgentStep localStepFor(String role) {
        return switch (role) {
            case "PM"        -> pmStep;
            case "QUALIFIER" -> qualifierStep;
            case "ARCHITECT" -> architectStep;
            case "TESTER"    -> testerStep;
            case "WRITER"    -> writerStep;
            default          -> throw new IllegalArgumentException("No local step for role: " + role);
        };
    }
}
