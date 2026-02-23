import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

// ── DTOs matching backend exactly ────────────────────────────────────────────

export interface RunsSummaryDto {
  totalRuns: number;
  successRate: number;
  failureRate: number;
  escalationRate: number;
  statusBreakdown: Record<string, number>;
  timeSeriesData?: {
    runCountByDate: Record<string, number>;
    successRateByDate: Record<string, number>;
    failureRateByDate: Record<string, number>;
    escalationRateByDate: Record<string, number>;
  };
}

export interface AgentMetrics {
  agentName: string;
  totalRuns: number;
  averageDuration: number;
  errorRate: number;
  successRate: number;
  averageCiFixCount: number;
  averageE2eFixCount: number;
}

export interface AgentsPerformanceDto {
  agentMetrics: AgentMetrics[];
  overallAverageDuration: number;
  overallErrorRate: number;
}

export interface PersonaStatistics {
  personaName: string;
  personaRole: string;
  totalFindings: number;
  criticalFindings: number;
  highFindings: number;
  mediumFindings: number;
  lowFindings: number;
  mandatoryFindings: number;
  averageFindingsPerRun: number;
}

export interface PersonasFindingsDto {
  personaStatistics: PersonaStatistics[];
  severityBreakdown: Record<string, { count: number; percentage: number }>;
  totalFindings: number;
  mandatoryFindings: number;
}

export interface FixLoopPattern {
  repo: string;
  issueNumber: number;
  ciFixCount: number;
  e2eFixCount: number;
  totalIterations: number;
  status: string;
  pattern: string;
}

export interface FixLoopsDto {
  patterns: FixLoopPattern[];
  loopStatistics: Record<string, { totalRuns: number; averageIterations: number; maxIterations: number; successRate: number }>;
  averageCiIterations: number;
  averageE2eIterations: number;
  runsWithMultipleCiIterations: number;
  runsWithMultipleE2eIterations: number;
}

export interface EscalationCluster {
  label: string;
  count: number;
  suggestedRootCause: string;
}

export interface KeywordInsight {
  keyword: string;
  frequency: number;
}

export interface EscalationInsightDto {
  totalEscalationsAnalysed: number;
  topErrorPatterns: Record<string, number>;
  problematicFiles: string[];
  clusters: EscalationCluster[];
  keywords?: KeywordInsight[];
  agentBottlenecks?: Record<string, number>;
}

export interface LatencyPoint {
  agentName: string;
  avgLatencyMs: number;
  callCount: number;
  totalTokens: number;
}

export interface TokenSummaryDto {
  totalTokens: number;
  llmCallCount: number;
  tokensByAgent: Record<string, number>;
}

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private http = inject(HttpClient);
  private base = '/api/analytics';

  getSummary(): Observable<RunsSummaryDto> {
    return this.http.get<RunsSummaryDto>(`${this.base}/runs/summary`);
  }

  getAgentPerformance(): Observable<AgentsPerformanceDto> {
    return this.http.get<AgentsPerformanceDto>(`${this.base}/agents/performance`);
  }

  getPersonasFindings(): Observable<PersonasFindingsDto> {
    return this.http.get<PersonasFindingsDto>(`${this.base}/personas/findings`);
  }

  getFixLoops(): Observable<FixLoopsDto> {
    return this.http.get<FixLoopsDto>(`${this.base}/fix-loops`);
  }

  generateEscalationInsights(): Observable<EscalationInsightDto> {
    return this.http.post<EscalationInsightDto>(`${this.base}/escalations/insights`, {});
  }

  getEscalationInsights(): Observable<EscalationInsightDto> {
    return this.http.post<EscalationInsightDto>(`${this.base}/escalations/insights`, {});
  }

  getLatencyTrend(): Observable<LatencyPoint[]> {
    return this.http.get<LatencyPoint[]>(`${this.base}/traces/latency-trend`);
  }

  getTraceSummary(runId: string): Observable<TokenSummaryDto> {
    return this.http.get<TokenSummaryDto>(`${this.base}/traces/summary?runId=${runId}`);
  }
}
