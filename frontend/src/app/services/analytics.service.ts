import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AnalyticsSummary {
  totalRuns: number;
  successRate: number;
  failureRate: number;
  escalationRate: number;
  statusBreakdown: Record<string, number>;
  repoBreakdown: Record<string, number>;
}

export interface AgentPerformance {
  avgDurationByAgent: Record<string, number>;
  errorRateByAgent: Record<string, number>;
  avgFixCountByStatus: Record<string, number>;
}

export interface EscalationInsight {
  totalEscalationsAnalysed: number;
  topErrorPatterns: Record<string, number>;
  problematicFiles: string[];
  clusters: EscalationCluster[];
}

export interface EscalationCluster {
  label: string;
  count: number;
  suggestedRootCause: string;
}

export interface PersonaEffectiveness {
  personaMetrics: Record<string, PersonaMetrics>;
  configurationRecommendations: string[];
}

export interface PersonaMetrics {
  reviewsCount: number;
  criticalFindings: number;
  effectivenessScore: number;
  falsePositives: number;
}

export interface Run {
  id: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  currentAgent: string;
  ciFixCount: number;
  e2eFixCount: number;
  artifacts: ArtifactSummary[];
  repo?: string;
  issueNumber?: number;
}

export interface ArtifactSummary {
  id: string;
  agentName: string;
  artifactType: string;
  createdAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class AnalyticsService {
  private http = inject(HttpClient);
  private apiUrl = '/api/analytics';
  private runsApiUrl = '/api/runs';

  getSummary(): Observable<AnalyticsSummary> {
    return this.http.get<AnalyticsSummary>(`${this.apiUrl}/runs/summary`);
  }

  getAgentPerformance(): Observable<AgentPerformance> {
    return this.http.get<AgentPerformance>(`${this.apiUrl}/agents/performance`);
  }

  getEscalationInsights(): Observable<EscalationInsight> {
    return this.http.get<EscalationInsight>(`${this.apiUrl}/escalations/insights`);
  }

  getPersonaEffectiveness(): Observable<PersonaEffectiveness> {
    return this.http.get<PersonaEffectiveness>(`${this.apiUrl}/personas/effectiveness`);
  }

  getRun(id: string): Observable<Run> {
    return this.http.get<Run>(`${this.runsApiUrl}/${id}`);
  }

  getRunArtifacts(id: string): Observable<ArtifactSummary[]> {
    return this.http.get<ArtifactSummary[]>(`${this.runsApiUrl}/${id}/artifacts`);
  }
}
