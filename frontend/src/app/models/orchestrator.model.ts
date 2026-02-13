export interface RunRequest {
    repo: string;
    issueNumber: number;
    mode: string;
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
    | 'ESCALATION_RAISED';

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
}
