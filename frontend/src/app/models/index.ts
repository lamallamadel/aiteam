export interface Run {
  id: string;
  status: RunStatus;
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

export type RunStatus = 
  | 'RECEIVED' 
  | 'PM' 
  | 'QUALIFIER' 
  | 'ARCHITECT' 
  | 'DEVELOPER' 
  | 'REVIEW' 
  | 'TESTER' 
  | 'WRITER' 
  | 'DONE' 
  | 'ESCALATED' 
  | 'FAILED';

export interface RunsPage {
  runs: Run[];
  total: number;
  page: number;
  pageSize: number;
}

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
