package com.atlasia.ai.service;

import com.atlasia.ai.service.observability.CorrelationIdHolder;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A2A Discovery Service — Agent-to-Agent Protocol Registry.
 *
 * Implements the A2A open standard for agent discovery and capability-based
 * routing. Replaces hardcoded agent-to-agent routing with dynamic discovery,
 * enabling hot-swapping, multi-vendor agents, and elastic team composition.
 *
 * Key concepts:
 *   Agent Card — A structured manifest describing an agent's capabilities,
 *                schemas, constraints, and health status.
 *   Capability Matching — Query the registry for agents matching required
 *                         capabilities, scored by coverage, effectiveness,
 *                         load, and cost efficiency.
 */
@Service
public class A2ADiscoveryService {
    private static final Logger log = LoggerFactory.getLogger(A2ADiscoveryService.class);

    private final OrchestratorMetrics metrics;

    /** In-memory agent registry: agent name → AgentCard. */
    private final ConcurrentHashMap<String, AgentCard> registry = new ConcurrentHashMap<>();

    /** Capability index: capability → set of agent names that provide it. */
    private final ConcurrentHashMap<String, Set<String>> capabilityIndex = new ConcurrentHashMap<>();

    /** Default agent mapping: role → default agent name (backward compatibility). */
    private static final Map<String, String> DEFAULT_AGENTS = Map.of(
            "PM", "pm-v1",
            "QUALIFIER", "qualifier-v1",
            "ARCHITECT", "architect-v1",
            "DEVELOPER", "developer-v1",
            "REVIEW", "review-v1",
            "TESTER", "tester-v1",
            "WRITER", "writer-v1",
            "JUDGE", "judge-v1"
    );

    public A2ADiscoveryService(OrchestratorMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Pre-populate the registry at startup with cards for every pipeline agent
     * and the orchestrator itself.
     */
    @PostConstruct
    public void registerDefaultAgents() {
        AgentConstraints defaultConstraints = new AgentConstraints(8192, 300_000, 0.10);

        register(new AgentCard(
                "pm-v1", "1.0", "PM", "atlasia",
                "Product Manager agent: analyzes tickets and extracts requirements",
                Set.of("ticket_analysis", "requirement_extraction", "issue_decomposition", "acceptance_criteria"),
                "ticket_plan", List.of(), defaultConstraints, "local", null, "active"));

        register(new AgentCard(
                "qualifier-v1", "1.0", "QUALIFIER", "atlasia",
                "Qualifier agent: estimates work, decomposes tasks, and scores scope",
                Set.of("work_estimation", "task_decomposition", "priority_scoring", "scope_analysis"),
                "work_plan", List.of(), defaultConstraints, "local", null, "active"));

        register(new AgentCard(
                "architect-v1", "1.0", "ARCHITECT", "atlasia",
                "Architect agent: designs system architecture and plans implementation",
                Set.of("system_design", "architecture_planning", "tech_selection", "dependency_analysis"),
                "architecture_notes", List.of(), defaultConstraints, "local", null, "active"));

        register(new AgentCard(
                "developer-v1", "1.0", "DEVELOPER", "atlasia",
                "Developer agent: generates code, runs security review, and commits changes",
                Set.of("code_generation", "security_review", "code_quality", "multi_file_commit"),
                "code_changes", List.of(), defaultConstraints, "local", null, "active"));

        register(new AgentCard(
                "reviewer-v1", "1.0", "REVIEW", "atlasia",
                "Reviewer agent: performs multi-persona code review and security audit",
                Set.of("code_review", "security_audit", "persona_review", "risk_assessment"),
                "persona_review_report", List.of(), defaultConstraints, "local", null, "active"));

        register(new AgentCard(
                "tester-v1", "1.0", "TESTER", "atlasia",
                "Tester agent: validates CI, generates tests, and applies fixes",
                Set.of("ci_validation", "test_generation", "e2e_validation", "fix_generation"),
                "test_report", List.of(), defaultConstraints, "local", null, "active"));

        register(new AgentCard(
                "writer-v1", "1.0", "WRITER", "atlasia",
                "Writer agent: produces documentation, changelogs, and inline comments",
                Set.of("documentation", "changelog", "api_docs", "inline_comments"),
                "docs_update", List.of(), defaultConstraints, "local", null, "active"));

        register(new AgentCard(
                "judge-v1", "1.0", "JUDGE", "atlasia",
                "Judge agent: arbitrates quality decisions using majority voting",
                Set.of("quality_arbitration", "majority_voting", "veto", "criterion_scoring"),
                "judge_verdict", List.of(), defaultConstraints, "local", null, "active"));

        log.info("A2A registry pre-populated with {} default agents", registry.size());
    }

    /**
     * Returns the orchestrator's own AgentCard for A2A discovery (/.well-known/agent.json).
     */
    public AgentCard getOrchestratorCard() {
        AgentConstraints constraints = new AgentConstraints(32768, 1_800_000, 1.00);
        return new AgentCard(
                "atlasia-orchestrator", "1.0", "ORCHESTRATOR", "atlasia",
                "Atlasia AI Orchestrator: full PM→Qualifier→Architect→Developer→Review→Tester→Writer pipeline",
                Set.of("ticket_analysis", "requirement_extraction", "system_design", "code_generation",
                        "code_review", "ci_validation", "documentation", "quality_arbitration",
                        "majority_voting", "multi_agent_pipeline"),
                "pipeline_result",
                List.of(),
                constraints,
                "http",
                "/actuator/health",
                "active");
    }

    /**
     * Register an agent's card in the registry.
     * If an agent with the same name already exists, it is replaced.
     */
    public void register(AgentCard card) {
        registry.put(card.name, card);

        // Update capability index
        for (String capability : card.capabilities) {
            capabilityIndex.computeIfAbsent(capability, k -> ConcurrentHashMap.newKeySet()).add(card.name);
        }

        log.info("A2A REGISTER: agent={}, version={}, capabilities={}, transport={}",
                card.name, card.version, card.capabilities, card.transport);
    }

    /**
     * Deregister an agent from the registry.
     */
    public void deregister(String agentName) {
        AgentCard removed = registry.remove(agentName);
        if (removed != null) {
            for (String capability : removed.capabilities) {
                Set<String> agents = capabilityIndex.get(capability);
                if (agents != null) {
                    agents.remove(agentName);
                }
            }
            log.info("A2A DEREGISTER: agent={}", agentName);
        }
    }

    /**
     * Discover agents matching ALL required capabilities.
     * Returns agents sorted by matching score (best fit first).
     *
     * @param requiredCapabilities capabilities the agent must support
     * @return list of matching agent cards, sorted by score descending
     */
    public List<AgentCard> discover(Set<String> requiredCapabilities) {
        metrics.recordA2ADiscovery("discovery");
        return registry.values().stream()
                .filter(card -> "active".equals(card.status))
                .map(card -> new ScoredAgent(card, computeMatchScore(card, requiredCapabilities)))
                .filter(sa -> sa.score > 0)
                .sorted(Comparator.comparingDouble(ScoredAgent::score).reversed())
                .map(ScoredAgent::card)
                .collect(Collectors.toList());
    }

    /**
     * Discover the best agent for a given pipeline role.
     * Uses capability matching first; falls back to the default agent.
     *
     * @param role the pipeline role (e.g., "DEVELOPER", "REVIEW")
     * @param requiredCapabilities additional capabilities needed for this task
     * @return the best matching agent card, or the default for the role
     */
    public AgentCard discoverForRole(String role, Set<String> requiredCapabilities) {
        metrics.recordA2ADiscovery(role);
        List<AgentCard> matches = discover(requiredCapabilities);

        if (!matches.isEmpty()) {
            AgentCard best = matches.get(0);
            log.info("A2A DISCOVER: role={}, bestMatch={}, score={}",
                    role, best.name, computeMatchScore(best, requiredCapabilities));
            return best;
        }

        // Fallback to default agent for this role
        String defaultName = DEFAULT_AGENTS.get(role);
        if (defaultName != null) {
            AgentCard defaultCard = registry.get(defaultName);
            if (defaultCard != null) {
                log.info("A2A DISCOVER: role={}, fallback to default={}", role, defaultName);
                return defaultCard;
            }
        }

        log.warn("A2A DISCOVER: no agent found for role={}, capabilities={}", role, requiredCapabilities);
        return null;
    }

    /**
     * Get an agent card by name.
     */
    public AgentCard getAgent(String agentName) {
        return registry.get(agentName);
    }

    /**
     * List all registered agents.
     */
    public Collection<AgentCard> listAgents() {
        return Collections.unmodifiableCollection(registry.values());
    }

    /**
     * List all agents with a specific status.
     */
    public List<AgentCard> listAgentsByStatus(String status) {
        return registry.values().stream()
                .filter(card -> status.equals(card.status))
                .collect(Collectors.toList());
    }

    /**
     * Update an agent's status (e.g., "active" → "degraded").
     */
    public void updateStatus(String agentName, String newStatus) {
        AgentCard card = registry.get(agentName);
        if (card != null) {
            AgentCard updated = new AgentCard(
                    card.name, card.version, card.role, card.vendor,
                    card.description, card.capabilities, card.outputArtifactKey,
                    card.mcpServers, card.constraints, card.transport,
                    card.healthEndpoint, newStatus);
            registry.put(agentName, updated);
            log.info("A2A STATUS UPDATE: agent={}, oldStatus={}, newStatus={}",
                    agentName, card.status, newStatus);
        }
    }

    /**
     * Check how many agents are registered and active.
     */
    public RegistryStats getStats() {
        long total = registry.size();
        long active = registry.values().stream().filter(c -> "active".equals(c.status)).count();
        long degraded = registry.values().stream().filter(c -> "degraded".equals(c.status)).count();
        long inactive = registry.values().stream().filter(c -> "inactive".equals(c.status)).count();
        return new RegistryStats(total, active, degraded, inactive);
    }

    /**
     * List all agents in the registry.
     */
    public List<AgentCard> listAllAgents() {
        return new ArrayList<>(registry.values());
    }

    // --- Scoring ---

    /**
     * Compute match score for an agent against required capabilities.
     *
     * Score = (capability_coverage * 0.4) +
     *         (historical_effectiveness * 0.3) +  // TODO: integrate with evaluation framework
     *         ((1 - normalized_load) * 0.15) +
     *         ((1 - normalized_cost) * 0.15)
     *
     * For now, uses capability_coverage as primary score.
     */
    private double computeMatchScore(AgentCard card, Set<String> required) {
        if (required.isEmpty()) return 1.0;

        long matched = required.stream().filter(card.capabilities::contains).count();
        double capabilityCoverage = (double) matched / required.size();

        // Currently using capability coverage only; other factors added as
        // the evaluation framework and load balancing are implemented.
        return capabilityCoverage;
    }

    // --- Data Classes ---

    public record AgentCard(
            String name, String version, String role, String vendor,
            String description, Set<String> capabilities, String outputArtifactKey,
            List<String> mcpServers, AgentConstraints constraints, String transport,
            String healthEndpoint, String status) {}

    public record AgentConstraints(int maxTokens, long maxDurationMs, double costBudgetUsd) {}

    public record RegistryStats(long total, long active, long degraded, long inactive) {}

    private record ScoredAgent(AgentCard card, double score) {}
}
