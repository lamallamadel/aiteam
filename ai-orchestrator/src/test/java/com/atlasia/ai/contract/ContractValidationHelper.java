package com.atlasia.ai.contract;

import com.atlasia.ai.service.A2ADiscoveryService.AgentCard;
import com.atlasia.ai.service.A2ADiscoveryService.AgentConstraints;
import com.atlasia.ai.service.AgentBindingService.AgentBinding;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Helper utilities for A2A contract validation.
 * 
 * Provides common validation methods and pattern matchers used across
 * contract tests to ensure consistency and reduce duplication.
 */
public class ContractValidationHelper {

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"
    );

    private static final Pattern VERSION_PATTERN = Pattern.compile(
        "[0-9]+\\.[0-9]+(\\.[0-9]+)?"
    );

    private static final Pattern ROLE_PATTERN = Pattern.compile(
        "[A-Z_]+"
    );

    private static final Pattern STATUS_PATTERN = Pattern.compile(
        "active|degraded|inactive"
    );

    private static final Pattern REPO_PATTERN = Pattern.compile(
        "[a-zA-Z0-9\\-_]+/[a-zA-Z0-9\\-_]+"
    );

    private static final Pattern ISO8601_PATTERN = Pattern.compile(
        "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]+)?Z?"
    );

    private static final Pattern AGENT_NAME_PATTERN = Pattern.compile(
        "[a-zA-Z0-9\\-_]+"
    );

    public static boolean isValidUUID(String value) {
        return UUID_PATTERN.matcher(value).matches();
    }

    public static boolean isValidVersion(String value) {
        return VERSION_PATTERN.matcher(value).matches();
    }

    public static boolean isValidRole(String value) {
        return ROLE_PATTERN.matcher(value).matches();
    }

    public static boolean isValidStatus(String value) {
        return STATUS_PATTERN.matcher(value).matches();
    }

    public static boolean isValidRepo(String value) {
        return REPO_PATTERN.matcher(value).matches();
    }

    public static boolean isValidISO8601(String value) {
        return ISO8601_PATTERN.matcher(value).matches();
    }

    public static boolean isValidAgentName(String value) {
        return AGENT_NAME_PATTERN.matcher(value).matches();
    }

    public static boolean validateAgentCardStructure(AgentCard card) {
        if (card == null) return false;
        if (!isValidAgentName(card.name())) return false;
        if (!isValidVersion(card.version())) return false;
        if (!isValidRole(card.role())) return false;
        if (!isValidStatus(card.status())) return false;
        if (card.capabilities() == null || card.capabilities().isEmpty()) return false;
        if (card.constraints() == null) return false;
        return validateConstraints(card.constraints());
    }

    public static boolean validateConstraints(AgentConstraints constraints) {
        if (constraints == null) return false;
        if (constraints.maxTokens() <= 0) return false;
        if (constraints.maxDurationMs() <= 0) return false;
        if (constraints.costBudgetUsd() < 0) return false;
        return true;
    }

    public static boolean validateAgentBinding(AgentBinding binding) {
        if (binding == null) return false;
        if (binding.bindingId() == null) return false;
        if (binding.runId() == null) return false;
        if (!isValidAgentName(binding.agentName())) return false;
        if (!isValidRole(binding.role())) return false;
        if (binding.signature() == null || binding.signature().isBlank()) return false;
        if (binding.issuedAt() == null || binding.expiresAt() == null) return false;
        if (!binding.expiresAt().isAfter(binding.issuedAt())) return false;
        if (binding.constraints() == null) return false;
        return validateConstraints(binding.constraints());
    }

    public static boolean validateCapabilityMatch(Set<String> declared, Set<String> required) {
        if (declared == null || required == null) return false;
        return declared.containsAll(required);
    }

    public static double computeCapabilityCoverage(Set<String> declared, Set<String> required) {
        if (required == null || required.isEmpty()) return 1.0;
        if (declared == null) return 0.0;
        
        long matched = required.stream()
            .filter(declared::contains)
            .count();
        
        return (double) matched / required.size();
    }

    public static boolean isBindingExpired(AgentBinding binding) {
        return Instant.now().isAfter(binding.expiresAt());
    }

    public static boolean isBindingValid(AgentBinding binding) {
        return validateAgentBinding(binding) && !isBindingExpired(binding);
    }

    public static String getUUIDPattern() {
        return UUID_PATTERN.pattern();
    }

    public static String getVersionPattern() {
        return VERSION_PATTERN.pattern();
    }

    public static String getRolePattern() {
        return ROLE_PATTERN.pattern();
    }

    public static String getStatusPattern() {
        return STATUS_PATTERN.pattern();
    }

    public static String getRepoPattern() {
        return REPO_PATTERN.pattern();
    }

    public static String getISO8601Pattern() {
        return ISO8601_PATTERN.pattern();
    }

    public static String getAgentNamePattern() {
        return AGENT_NAME_PATTERN.pattern();
    }
}
