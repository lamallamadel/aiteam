export interface RunRequest {
    repo: string;
    issueNumber: number;
    mode: string;
    autonomy?: string; // "autonomous" (default) | "confirm" | "observe"
}

export interface RunResponse {
    id: string;
    repo: string;
    issueNumber: number;
    status: string;
    createdAt: string;
    updatedAt: string;
    currentAgent?: string;
    ciFixCount: number;
    e2eFixCount: number;
    artifacts?: ArtifactSummary[];
}

export interface ArtifactSummary {
    id: string;
    agentName: string;
    artifactType: string;
    createdAt: string;
}

export interface ArtifactResponse {
    id: string;
    agentName: string;
    artifactType: string;
    payload: string;
    createdAt: string;
}

export interface Persona {
    name: string;
    role: string;
    mission: string;
    focusAreas: string[];
}

export interface ChatRequest {
    message: string;
}

export interface ChatResponse {
    response: string;
}

// A2A Protocol models (matching backend A2ADiscoveryService + AgentBindingService)
export interface AgentConstraints {
    maxTokens: number;
    maxDurationMs: number;
    costBudgetUsd: number;
}

export interface AgentCard {
    name: string;
    version: string;
    role: string;
    vendor: string;
    description: string;
    capabilities: string[];
    outputArtifactKey: string;
    mcpServers: string[];
    constraints: AgentConstraints;
    transport: string;
    healthEndpoint: string | null;
    status: string;
}

export interface AgentBinding {
    bindingId: string;
    runId: string;
    agentName: string;
    role: string;
    declaredCapabilities: string[];
    requiredCapabilities: string[];
    constraints: AgentConstraints | null;
    issuedAt: string;
    expiresAt: string;
    signature: string;
}

export interface BindingVerification {
    binding: AgentBinding;
    valid: boolean;
}

export type EnvironmentLifecycle = 'ACTIVE' | 'PAUSED' | 'HANDED_OFF' | 'COMPLETED';

// SSE Workflow Event types (matching backend WorkflowEvent sealed interface)
export type WorkflowEventType =
    | 'STEP_START'
    | 'STEP_COMPLETE'
    | 'TOOL_CALL_START'
    | 'TOOL_CALL_END'
    | 'WORKFLOW_STATUS'
    | 'LLM_CALL_START'
    | 'LLM_CALL_END'
    | 'SCHEMA_VALIDATION'
    | 'WORKFLOW_ERROR'
    | 'ESCALATION_RAISED'
    | 'GRAFT_START'
    | 'GRAFT_COMPLETE'
    | 'GRAFT_FAILED';

export interface WorkflowEvent {
    runId: string;
    timestamp: string;
    eventType: WorkflowEventType;
    agentName?: string;
    // StepStart / StepComplete
    stepPhase?: string;
    durationMs?: number;
    artifactType?: string;
    // ToolCall
    toolName?: string;
    description?: string;
    // WorkflowStatus
    status?: string;
    currentAgent?: string;
    progressPercent?: number;
    // LlmCall
    model?: string;
    tokensUsed?: number;
    // SchemaValidation
    schemaName?: string;
    passed?: boolean;
    // Error / Escalation
    errorType?: string;
    message?: string;
    reason?: string;
    // Graft
    graftId?: string;
    checkpointAfter?: string;
    artifactId?: string;
}

export interface GraftExecution {
    id: string;
    runId: string;
    graftId: string;
    agentName: string;
    checkpointAfter: string;
    startedAt: string;
    completedAt: string | null;
    status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'TIMEOUT' | 'CIRCUIT_OPEN';
    outputArtifactId: string | null;
    errorMessage: string | null;
    retryCount: number;
    timeoutMs: number;
    createdAt: string;
    updatedAt: string;
}

export interface CircuitBreakerStatus {
    agentName: string;
    state: 'CLOSED' | 'OPEN' | 'HALF_OPEN';
    failureCount: number;
    lastFailureTime: string | null;
    successfulExecutions: number;
    failedExecutions: number;
    recentFailures: FailureRecord[];
    failureRate: number;
}

export interface FailureRecord {
    graftId: string;
    timestamp: string;
    errorMessage: string;
}

export interface GitProvider {
    provider: 'github' | 'gitlab' | 'bitbucket' | null;
    token: string | null;
    url: string | null;
    label: string | null;
}

export interface UsageData {
    tokenConsumption: number;
    budget: number;
}

export interface RateLimitConfig {
    rpm: number;
    tpm: number;
}

export interface AIPreferences {
    autonomyLevel: 'autonomous' | 'confirm' | 'observe';
    oversightRules: string[];
    systemInstructions: string | null;
}

export interface PendingInterrupt {
    runId: string;
    agentName: string;
    ruleName: string;
    tier: string;
    message: string;
    createdAt: string;
}

export interface InterruptDecisionRequest {
    decision: string;
    decidedBy: string;
    reason: string;
}
