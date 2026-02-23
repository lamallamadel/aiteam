import { Injectable, signal, computed } from '@angular/core';
import { GitProvider, UsageData, RateLimitConfig, AIPreferences } from '../models/orchestrator.model';

@Injectable({
    providedIn: 'root'
})
export class SettingsService {
    private readonly STORAGE_PREFIX = 'settings_';
    private readonly GIT_PROVIDER_KEY = `${this.STORAGE_PREFIX}git_provider`;
    private readonly USAGE_DATA_KEY = `${this.STORAGE_PREFIX}usage_data`;
    private readonly RATE_LIMIT_KEY = `${this.STORAGE_PREFIX}rate_limit`;
    private readonly AI_PREFERENCES_KEY = `${this.STORAGE_PREFIX}ai_preferences`;

    private readonly defaultGitProvider: GitProvider = {
        provider: null,
        token: null,
        url: null,
        label: null
    };

    private readonly defaultUsageData: UsageData = {
        tokenConsumption: 0,
        budget: 100000
    };

    private readonly defaultRateLimitConfig: RateLimitConfig = {
        rpm: 60,
        tpm: 90000
    };

    private readonly defaultAIPreferences: AIPreferences = {
        autonomyLevel: 'autonomous',
        oversightRules: [],
        systemInstructions: null
    };

    readonly gitProvider = signal<GitProvider>(this.loadGitProvider());
    readonly usageData = signal<UsageData>(this.loadUsageData());
    readonly rateLimitConfig = signal<RateLimitConfig>(this.loadRateLimitConfig());
    readonly aiPreferences = signal<AIPreferences>(this.loadAIPreferences());

    readonly hasGitProviderConfigured = computed(() => {
        const provider = this.gitProvider();
        return provider.provider !== null && provider.token !== null;
    });

    readonly tokenUsagePercent = computed(() => {
        const usage = this.usageData();
        if (usage.budget === 0) return 0;
        return Math.min(100, (usage.tokenConsumption / usage.budget) * 100);
    });

    readonly isOverBudget = computed(() => {
        const usage = this.usageData();
        return usage.tokenConsumption > usage.budget;
    });

    readonly rpmUtilization = computed(() => {
        const usage = this.usageData();
        const limit = this.rateLimitConfig();
        return {
            rpm: limit.rpm,
            tpm: limit.tpm,
            currentTokens: usage.tokenConsumption
        };
    });

    private loadGitProvider(): GitProvider {
        const stored = localStorage.getItem(this.GIT_PROVIDER_KEY);
        if (stored) {
            try {
                return JSON.parse(stored);
            } catch {
                return this.defaultGitProvider;
            }
        }
        return this.defaultGitProvider;
    }

    private loadUsageData(): UsageData {
        const stored = localStorage.getItem(this.USAGE_DATA_KEY);
        if (stored) {
            try {
                return JSON.parse(stored);
            } catch {
                return this.defaultUsageData;
            }
        }
        return this.defaultUsageData;
    }

    private loadRateLimitConfig(): RateLimitConfig {
        const stored = localStorage.getItem(this.RATE_LIMIT_KEY);
        if (stored) {
            try {
                return JSON.parse(stored);
            } catch {
                return this.defaultRateLimitConfig;
            }
        }
        return this.defaultRateLimitConfig;
    }

    private loadAIPreferences(): AIPreferences {
        const stored = localStorage.getItem(this.AI_PREFERENCES_KEY);
        if (stored) {
            try {
                return JSON.parse(stored);
            } catch {
                return this.defaultAIPreferences;
            }
        }
        return this.defaultAIPreferences;
    }

    updateGitProvider(provider: Partial<GitProvider>): void {
        const updated = { ...this.gitProvider(), ...provider };
        this.gitProvider.set(updated);
        localStorage.setItem(this.GIT_PROVIDER_KEY, JSON.stringify(updated));
    }

    updateUsageData(data: Partial<UsageData>): void {
        const updated = { ...this.usageData(), ...data };
        this.usageData.set(updated);
        localStorage.setItem(this.USAGE_DATA_KEY, JSON.stringify(updated));
    }

    updateRateLimitConfig(config: Partial<RateLimitConfig>): void {
        const updated = { ...this.rateLimitConfig(), ...config };
        this.rateLimitConfig.set(updated);
        localStorage.setItem(this.RATE_LIMIT_KEY, JSON.stringify(updated));
    }

    updateAIPreferences(preferences: Partial<AIPreferences>): void {
        const updated = { ...this.aiPreferences(), ...preferences };
        this.aiPreferences.set(updated);
        localStorage.setItem(this.AI_PREFERENCES_KEY, JSON.stringify(updated));
    }

    resetGitProvider(): void {
        this.gitProvider.set(this.defaultGitProvider);
        localStorage.removeItem(this.GIT_PROVIDER_KEY);
    }

    resetUsageData(): void {
        this.usageData.set(this.defaultUsageData);
        localStorage.removeItem(this.USAGE_DATA_KEY);
    }

    resetRateLimitConfig(): void {
        this.rateLimitConfig.set(this.defaultRateLimitConfig);
        localStorage.removeItem(this.RATE_LIMIT_KEY);
    }

    resetAIPreferences(): void {
        this.aiPreferences.set(this.defaultAIPreferences);
        localStorage.removeItem(this.AI_PREFERENCES_KEY);
    }

    resetAll(): void {
        this.resetGitProvider();
        this.resetUsageData();
        this.resetRateLimitConfig();
        this.resetAIPreferences();
    }

    incrementTokenConsumption(tokens: number): void {
        const current = this.usageData();
        this.updateUsageData({ tokenConsumption: current.tokenConsumption + tokens });
    }

    addOversightRule(rule: string): void {
        const current = this.aiPreferences();
        const rules = [...current.oversightRules, rule];
        this.updateAIPreferences({ oversightRules: rules });
    }

    removeOversightRule(index: number): void {
        const current = this.aiPreferences();
        const rules = current.oversightRules.filter((_, i) => i !== index);
        this.updateAIPreferences({ oversightRules: rules });
    }

    updateOversightRule(index: number, rule: string): void {
        const current = this.aiPreferences();
        const rules = [...current.oversightRules];
        rules[index] = rule;
        this.updateAIPreferences({ oversightRules: rules });
    }
}
