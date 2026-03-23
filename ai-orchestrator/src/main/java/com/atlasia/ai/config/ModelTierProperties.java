package com.atlasia.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Budget-aware model tier routing: dual primary/secondary legs per complexity level.
 * Bound from {@code atlasia.model-tiers} in application.yml.
 */
@ConfigurationProperties(prefix = "atlasia.model-tiers")
public class ModelTierProperties {

    private Map<String, TierDefinition> tiers = new LinkedHashMap<>();
    private Budget budget = new Budget();
    private Map<String, String> agentComplexity = new LinkedHashMap<>();

    public Map<String, TierDefinition> getTiers() {
        return tiers;
    }

    public void setTiers(Map<String, TierDefinition> tiers) {
        this.tiers = tiers != null ? tiers : new LinkedHashMap<>();
    }

    public Budget getBudget() {
        return budget;
    }

    public void setBudget(Budget budget) {
        this.budget = budget != null ? budget : new Budget();
    }

    public Map<String, String> getAgentComplexity() {
        return agentComplexity;
    }

    public void setAgentComplexity(Map<String, String> agentComplexity) {
        this.agentComplexity = agentComplexity != null ? agentComplexity : new LinkedHashMap<>();
    }

    public static class TierDefinition {
        private DualDefinition dual;
        /** prefer-primary | sticky-until-failure */
        private String availability = "prefer-primary";

        public DualDefinition getDual() {
            return dual;
        }

        public void setDual(DualDefinition dual) {
            this.dual = dual;
        }

        public String getAvailability() {
            return availability;
        }

        public void setAvailability(String availability) {
            this.availability = availability != null ? availability : "prefer-primary";
        }
    }

    public static class DualDefinition {
        private LegDefinition primary;
        private LegDefinition secondary;

        public LegDefinition getPrimary() {
            return primary;
        }

        public void setPrimary(LegDefinition primary) {
            this.primary = primary;
        }

        public LegDefinition getSecondary() {
            return secondary;
        }

        public void setSecondary(LegDefinition secondary) {
            this.secondary = secondary;
        }
    }

    public static class LegDefinition {
        private String providerId;
        private String model;
        private double costPer1kInput = 0.005;
        private double costPer1kOutput = 0.015;

        public String getProviderId() {
            return providerId;
        }

        public void setProviderId(String providerId) {
            this.providerId = providerId;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getCostPer1kInput() {
            return costPer1kInput;
        }

        public void setCostPer1kInput(double costPer1kInput) {
            this.costPer1kInput = costPer1kInput;
        }

        public double getCostPer1kOutput() {
            return costPer1kOutput;
        }

        public void setCostPer1kOutput(double costPer1kOutput) {
            this.costPer1kOutput = costPer1kOutput;
        }
    }

    public static class Budget {
        private double maxPerRunUsd = 100.0;
        private double maxDailyUsd = 500.0;
        private double downgradeThreshold = 0.80;

        public double getMaxPerRunUsd() {
            return maxPerRunUsd;
        }

        public void setMaxPerRunUsd(double maxPerRunUsd) {
            this.maxPerRunUsd = maxPerRunUsd;
        }

        public double getMaxDailyUsd() {
            return maxDailyUsd;
        }

        public void setMaxDailyUsd(double maxDailyUsd) {
            this.maxDailyUsd = maxDailyUsd;
        }

        public double getDowngradeThreshold() {
            return downgradeThreshold;
        }

        public void setDowngradeThreshold(double downgradeThreshold) {
            this.downgradeThreshold = downgradeThreshold;
        }
    }
}
