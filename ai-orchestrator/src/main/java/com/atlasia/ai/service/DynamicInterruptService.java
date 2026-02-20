package com.atlasia.ai.service;

import com.atlasia.ai.service.event.WorkflowEvent;
import com.atlasia.ai.service.event.WorkflowEventBus;
import com.atlasia.ai.service.observability.CorrelationIdHolder;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Dynamic Interrupt Service — Runtime Guardrails for Agent Actions.
 *
 * Monitors agent tool calls and actions in real-time, pausing execution
 * when high-risk operations are detected. Implements the "Secure-by-Design"
 * principle: agents are treated like junior employees with admin access.
 *
 * Interrupt tiers:
 *   CRITICAL — Always block, require explicit human approval
 *   HIGH     — Block by default, auto-approve for known-safe patterns
 *   MEDIUM   — Log and notify, auto-approve after delay
 *   LOW      — Log only
 */
@Service
public class DynamicInterruptService {
    private static final Logger log = LoggerFactory.getLogger(DynamicInterruptService.class);

    private final OrchestratorMetrics metrics;
    private final WorkflowEventBus eventBus;

    /**
     * Protected branch patterns that trigger critical interrupts.
     */
    private static final Set<String> PROTECTED_BRANCHES = Set.of(
            "main", "master", "production", "staging"
    );

    /**
     * Destructive SQL patterns that trigger critical interrupts.
     */
    private static final List<Pattern> DESTRUCTIVE_SQL_PATTERNS = List.of(
            Pattern.compile("(?i)DROP\\s+(TABLE|COLUMN|INDEX|DATABASE)"),
            Pattern.compile("(?i)TRUNCATE\\s+"),
            Pattern.compile("(?i)DELETE\\s+FROM\\s+\\S+\\s*$"),
            Pattern.compile("(?i)ALTER\\s+TABLE.*DROP")
    );

    /**
     * Secret patterns that trigger critical interrupts.
     */
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("(?i)(API[_-]?KEY|SECRET[_-]?KEY|PASSWORD|PRIVATE[_-]?KEY|ACCESS[_-]?TOKEN)\\s*[=:]\\s*['\"]?[A-Za-z0-9+/=_-]{16,}"),
            Pattern.compile("-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----"),
            Pattern.compile("(?i)(ghp_|gho_|ghs_|github_pat_)[A-Za-z0-9_]{36,}"),
            Pattern.compile("(?i)sk-[A-Za-z0-9]{32,}"),
            Pattern.compile("(?i)AKIA[0-9A-Z]{16}")
    );

    /**
     * File paths that are always denied for writes.
     */
    private static final List<Pattern> DENIED_WRITE_PATHS = List.of(
            Pattern.compile("(?i)\\.env(\\..+)?$"),
            Pattern.compile("(?i)secrets?/"),
            Pattern.compile("(?i)credentials"),
            Pattern.compile("(?i)\\.ssh/"),
            Pattern.compile("(?i)\\.aws/"),
            Pattern.compile("(?i)\\.github/workflows/")
    );

    public DynamicInterruptService(OrchestratorMetrics metrics, WorkflowEventBus eventBus) {
        this.metrics = metrics;
        this.eventBus = eventBus;
    }

    /**
     * Evaluate a tool call against interrupt rules BEFORE execution.
     * Returns an InterruptDecision indicating whether to proceed, block, or pause.
     *
     * @param agentName   the agent making the tool call
     * @param serverName  the MCP server being invoked
     * @param toolName    the specific tool being called
     * @param arguments   the tool call arguments
     * @param runId       the workflow run ID
     * @return the interrupt decision
     */
    public InterruptDecision evaluate(String agentName, String serverName, String toolName,
                                       Map<String, Object> arguments, UUID runId) {
        String correlationId = CorrelationIdHolder.getCorrelationId();

        // Evaluate rules in tier order: critical → high → medium → low
        // Short-circuit on first match.

        // --- CRITICAL TIER ---

        // Rule: Destructive git operations
        if ("git".equals(serverName) && "git_push".equals(toolName)) {
            String targetBranch = getStringArg(arguments, "branch", "");
            boolean forcePush = getBoolArg(arguments, "force", false);

            if (PROTECTED_BRANCHES.contains(targetBranch) || forcePush) {
                return criticalInterrupt(runId, agentName, "destructive_git_operations",
                        String.format("Agent '%s' attempting %s to protected branch '%s' (force=%s)",
                                agentName, toolName, targetBranch, forcePush));
            }
        }

        // Rule: Destructive SQL
        if ("postgres".equals(serverName) && "run_query".equals(toolName)) {
            String query = getStringArg(arguments, "query", "");
            for (Pattern pattern : DESTRUCTIVE_SQL_PATTERNS) {
                if (pattern.matcher(query).find()) {
                    return criticalInterrupt(runId, agentName, "database_schema_mutations",
                            String.format("Agent '%s' attempting destructive SQL: %s",
                                    agentName, truncate(query, 100)));
                }
            }
        }

        // Rule: Secret exposure in writes
        if ("filesystem".equals(serverName) && "write_file".equals(toolName)) {
            String content = getStringArg(arguments, "content", "");
            String filePath = getStringArg(arguments, "path", "");

            for (Pattern pattern : SECRET_PATTERNS) {
                if (pattern.matcher(content).find()) {
                    return blockInterrupt(runId, agentName, "secret_exposure",
                            String.format("Agent '%s' attempting to write secrets to '%s'",
                                    agentName, filePath));
                }
            }

            // Rule: Write to denied paths
            for (Pattern pattern : DENIED_WRITE_PATHS) {
                if (pattern.matcher(filePath).find()) {
                    return criticalInterrupt(runId, agentName, "file_writes_outside_scope",
                            String.format("Agent '%s' attempting to write to denied path '%s'",
                                    agentName, filePath));
                }
            }
        }

        // Rule: Secret exposure in commits
        if ("git".equals(serverName) && "git_commit".equals(toolName)) {
            String message = getStringArg(arguments, "message", "");
            for (Pattern pattern : SECRET_PATTERNS) {
                if (pattern.matcher(message).find()) {
                    return blockInterrupt(runId, agentName, "secret_exposure",
                            String.format("Agent '%s' attempting to commit with secrets in message", agentName));
                }
            }
        }

        // --- HIGH TIER ---

        // Rule: Large code changes
        if ("git".equals(serverName) && "git_commit".equals(toolName)) {
            int filesChanged = getIntArg(arguments, "files_changed", 0);
            int linesAdded = getIntArg(arguments, "lines_added", 0);
            if (filesChanged > 20 || linesAdded > 1000) {
                return highInterrupt(runId, agentName, "large_code_changes",
                        String.format("Agent '%s' committing large change: %d files, +%d lines",
                                agentName, filesChanged, linesAdded));
            }
        }

        // Rule: Dependency introduction
        if ("filesystem".equals(serverName) && "write_file".equals(toolName)) {
            String filePath = getStringArg(arguments, "path", "");
            if (filePath.endsWith("pom.xml") || filePath.endsWith("package.json") || filePath.endsWith("build.gradle")) {
                return highInterrupt(runId, agentName, "dependency_introduction",
                        String.format("Agent '%s' modifying dependency manifest: %s", agentName, filePath));
            }
        }

        // --- MEDIUM TIER ---

        // Rule: PR creation
        if ("github".equals(serverName) && "create_pull_request".equals(toolName)) {
            String title = getStringArg(arguments, "title", "");
            log.info("INTERRUPT NOTICE: Agent '{}' creating PR: '{}', runId={}, correlationId={}",
                    agentName, title, runId, correlationId);
            return mediumInterrupt(runId, agentName, "pr_creation",
                    String.format("Agent '%s' creating PR: '%s'", agentName, title));
        }

        // --- LOW TIER (log only) ---
        log.debug("INTERRUPT EVALUATION: no rules matched for agent={}, server={}, tool={}, runId={}",
                agentName, serverName, toolName, runId);

        return InterruptDecision.proceed();
    }

    /**
     * Check agent token budget and emit warning if approaching limit.
     */
    public void checkTokenBudget(String agentName, int tokensUsed, int maxTokens, UUID runId) {
        if (maxTokens > 0 && tokensUsed > (int) (maxTokens * 0.8)) {
            log.warn("TOKEN BUDGET WARNING: agent={}, used={}/{} ({}%), runId={}",
                    agentName, tokensUsed, maxTokens,
                    (int) ((double) tokensUsed / maxTokens * 100), runId);
            metrics.recordTokenBudgetExceeded(agentName);
        }
    }

    /**
     * Check step duration and emit warning if approaching limit.
     */
    public void checkDurationBudget(String agentName, long durationMs, long maxDurationMs, UUID runId) {
        if (maxDurationMs > 0 && durationMs > (long) (maxDurationMs * 0.8)) {
            log.warn("DURATION BUDGET WARNING: agent={}, elapsed={}ms/{}ms ({}%), runId={}",
                    agentName, durationMs, maxDurationMs,
                    (int) ((double) durationMs / maxDurationMs * 100), runId);
        }
    }

    // --- Private helper methods ---

    private InterruptDecision criticalInterrupt(UUID runId, String agentName, String ruleName, String message) {
        log.warn("CRITICAL INTERRUPT: rule={}, agent={}, runId={}, correlationId={}, message={}",
                ruleName, agentName, runId, CorrelationIdHolder.getCorrelationId(), message);
        metrics.recordGuardrailViolation(ruleName, agentName);

        eventBus.emit(runId, new WorkflowEvent.WorkflowStatusUpdate(
                runId, Instant.now(), "INTERRUPT_CRITICAL: " + message, agentName, -1));

        return InterruptDecision.pauseAndNotify("critical", ruleName, message);
    }

    private InterruptDecision highInterrupt(UUID runId, String agentName, String ruleName, String message) {
        log.warn("HIGH INTERRUPT: rule={}, agent={}, runId={}, correlationId={}, message={}",
                ruleName, agentName, runId, CorrelationIdHolder.getCorrelationId(), message);
        metrics.recordGuardrailViolation(ruleName, agentName);

        eventBus.emit(runId, new WorkflowEvent.WorkflowStatusUpdate(
                runId, Instant.now(), "INTERRUPT_HIGH: " + message, agentName, -1));

        return InterruptDecision.pauseAndNotify("high", ruleName, message);
    }

    private InterruptDecision mediumInterrupt(UUID runId, String agentName, String ruleName, String message) {
        log.info("MEDIUM INTERRUPT: rule={}, agent={}, runId={}, correlationId={}, message={}",
                ruleName, agentName, runId, CorrelationIdHolder.getCorrelationId(), message);

        return InterruptDecision.notifyAndProceed("medium", ruleName, message);
    }

    private InterruptDecision blockInterrupt(UUID runId, String agentName, String ruleName, String message) {
        log.error("BLOCKED: rule={}, agent={}, runId={}, correlationId={}, message={}",
                ruleName, agentName, runId, CorrelationIdHolder.getCorrelationId(), message);
        metrics.recordGuardrailViolation(ruleName, agentName);

        eventBus.emit(runId, new WorkflowEvent.WorkflowStatusUpdate(
                runId, Instant.now(), "BLOCKED: " + message, agentName, -1));

        return InterruptDecision.block(ruleName, message);
    }

    private String getStringArg(Map<String, Object> args, String key, String defaultValue) {
        Object val = args.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private boolean getBoolArg(Map<String, Object> args, String key, boolean defaultValue) {
        Object val = args.get(key);
        if (val instanceof Boolean b) return b;
        return defaultValue;
    }

    private int getIntArg(Map<String, Object> args, String key, int defaultValue) {
        Object val = args.get(key);
        if (val instanceof Number n) return n.intValue();
        return defaultValue;
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // --- Decision Result ---

    /**
     * Result of evaluating a tool call against interrupt rules.
     */
    public static class InterruptDecision {
        public enum Action { PROCEED, PAUSE_AND_NOTIFY, NOTIFY_AND_PROCEED, BLOCK }

        private final Action action;
        private final String tier;
        private final String ruleName;
        private final String message;

        private InterruptDecision(Action action, String tier, String ruleName, String message) {
            this.action = action;
            this.tier = tier;
            this.ruleName = ruleName;
            this.message = message;
        }

        public static InterruptDecision proceed() {
            return new InterruptDecision(Action.PROCEED, null, null, null);
        }

        public static InterruptDecision pauseAndNotify(String tier, String ruleName, String message) {
            return new InterruptDecision(Action.PAUSE_AND_NOTIFY, tier, ruleName, message);
        }

        public static InterruptDecision notifyAndProceed(String tier, String ruleName, String message) {
            return new InterruptDecision(Action.NOTIFY_AND_PROCEED, tier, ruleName, message);
        }

        public static InterruptDecision block(String ruleName, String message) {
            return new InterruptDecision(Action.BLOCK, "critical", ruleName, message);
        }

        public Action getAction() { return action; }
        public String getTier() { return tier; }
        public String getRuleName() { return ruleName; }
        public String getMessage() { return message; }

        public boolean shouldProceed() {
            return action == Action.PROCEED || action == Action.NOTIFY_AND_PROCEED;
        }

        public boolean requiresHumanApproval() {
            return action == Action.PAUSE_AND_NOTIFY;
        }

        public boolean isBlocked() {
            return action == Action.BLOCK;
        }
    }
}
