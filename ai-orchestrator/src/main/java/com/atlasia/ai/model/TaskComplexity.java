package com.atlasia.ai.model;

import java.util.Locale;

/**
 * Pipeline LLM complexity tier (maps to {@code atlasia.model-tiers.tiers.*} in YAML).
 */
public enum TaskComplexity {
    VERY_HIGH,
    HIGH,
    MEDIUM,
    MEDIUM_LOW,
    LOW,
    TRIVIAL;

    /** Next cheaper tier, or {@code this} if already {@link #TRIVIAL}. */
    public TaskComplexity downgrade() {
        TaskComplexity[] v = values();
        int i = ordinal();
        if (i >= v.length - 1) {
            return this;
        }
        return v[i + 1];
    }

    /** Kebab-case key under {@code atlasia.model-tiers.tiers}. */
    public String yamlKey() {
        return switch (this) {
            case VERY_HIGH -> "very-high";
            case HIGH -> "high";
            case MEDIUM -> "medium";
            case MEDIUM_LOW -> "medium-low";
            case LOW -> "low";
            case TRIVIAL -> "trivial";
        };
    }

    public static TaskComplexity fromYamlKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEDIUM;
        }
        String k = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (k) {
            case "very-high", "very_high" -> VERY_HIGH;
            case "high" -> HIGH;
            case "medium" -> MEDIUM;
            case "medium-low", "medium_low" -> MEDIUM_LOW;
            case "low" -> LOW;
            case "trivial" -> TRIVIAL;
            default -> MEDIUM;
        };
    }
}
