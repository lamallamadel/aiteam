import { Injectable, signal, computed } from '@angular/core';
import { GitProvider, UsageData, RateLimitConfig, AIPreferences } from '../models/orchestrator.model';

export interface GitProviderWithId extends GitProvider {
    id: string;
    status?: 'connected' | 'disconnected' | 'error';
}

export interface UsageHistory {
    timestamp: string;
    tokens: number;
}

@Injectable({
    providedIn: 'root'
})
export class SettingsService {
    private readonly STORAGE_PREFIX = 'settings_';
    private readonly GIT_PROVIDER_KEY = `${this.STORAGE_PREFIX}git_provider`;
    private readonly GIT_PROVIDERS_KEY = `${this.STORAGE_PREFIX}git_providers`;
    private readonly USAGE_DATA_KEY = `${this.STORAGE_PREFIX}usage_data`;
    private readonly USAGE_HISTORY_KEY = `${this.STORAGE_PREFIX}usage_history`;
    private readonly RATE_LIMIT_KEY = `${this.STORAGE_PREFIX}rate_limit`;
    private readonly AI_PREFERENCES_KEY = `${this.STORAGE_PREFIX}ai_preferences`;
    private readonly MONTHLY_REQUESTS_KEY = `${this.STORAGE_PREFIX}monthly_requests`;

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
    readonly gitProviders = signal<GitProviderWithId[]>(this.loadGitProviders());
    readonly usageData = signal<UsageData>(this.loadUsageData());
    readonly usageHistory = signal<UsageHistory[]>(this.loadUsageHistory());
    readonly monthlyRequests = signal<number>(this.loadMonthlyRequests());
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

    private loadGitProviders(): GitProviderWithId[] {
        const stored = localStorage.getItem(this.GIT_PROVIDERS_KEY);
        if (stored) {
            try {
                return JSON.parse(stored);
            } catch {
                return [];
            }
        }
        return [];
    }

    private loadUsageHistory(): UsageHistory[] {
        const stored = localStorage.getItem(this.USAGE_HISTORY_KEY);
        if (stored) {
            try {
                return JSON.parse(stored);
            } catch {
                return [];
            }
        }
        return [];
    }

    private loadMonthlyRequests(): number {
        const stored = localStorage.getItem(this.MONTHLY_REQUESTS_KEY);
        if (stored) {
            try {
                return parseInt(stored, 10);
            } catch {
                return 0;
            }
        }
        return 0;
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

    addGitProvider(provider: Omit<GitProviderWithId, 'id'>): void {
        const newProvider: GitProviderWithId = {
            ...provider,
            id: `provider_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
            status: provider.status || 'connected'
        };
        const updated = [...this.gitProviders(), newProvider];
        this.gitProviders.set(updated);
        localStorage.setItem(this.GIT_PROVIDERS_KEY, JSON.stringify(updated));
    }

    updateGitProviderById(id: string, updates: Partial<GitProviderWithId>): void {
        const updated = this.gitProviders().map(p => p.id === id ? { ...p, ...updates } : p);
        this.gitProviders.set(updated);
        localStorage.setItem(this.GIT_PROVIDERS_KEY, JSON.stringify(updated));
    }

    removeGitProvider(id: string): void {
        const updated = this.gitProviders().filter(p => p.id !== id);
        this.gitProviders.set(updated);
        localStorage.setItem(this.GIT_PROVIDERS_KEY, JSON.stringify(updated));
    }

    addUsageHistoryPoint(tokens: number): void {
        const newPoint: UsageHistory = {
            timestamp: new Date().toISOString(),
            tokens
        };
        const history = [...this.usageHistory(), newPoint].slice(-30);
        this.usageHistory.set(history);
        localStorage.setItem(this.USAGE_HISTORY_KEY, JSON.stringify(history));
    }

    incrementMonthlyRequests(count: number = 1): void {
        const newCount = this.monthlyRequests() + count;
        this.monthlyRequests.set(newCount);
        localStorage.setItem(this.MONTHLY_REQUESTS_KEY, newCount.toString());
    }

    resetMonthlyRequests(): void {
        this.monthlyRequests.set(0);
        localStorage.removeItem(this.MONTHLY_REQUESTS_KEY);
    }
}
