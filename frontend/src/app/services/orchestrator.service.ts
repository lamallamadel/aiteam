import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RunRequest, RunResponse, ArtifactResponse, Persona, ChatResponse, AgentCard, AgentBinding, GraftExecution, CircuitBreakerStatus, PendingInterrupt, InterruptDecisionRequest, CurrentUserDto, UserRegistrationRequest, UserRegistrationResponse } from '../models/orchestrator.model';
import { CollaborationEventEntity } from '../models/collaboration.model';

export interface OversightInterruptRule {
  ruleName: string;
  tier: string;
  enabled: boolean;
}

export interface OversightConfigPayload {
  autonomyLevel: string;
  interruptRules: OversightInterruptRule[];
  autoApproveMedianTier: boolean;
  maxConcurrentRuns: number;
}

@Injectable({
    providedIn: 'root'
})
export class OrchestratorService {
    private apiUrl = '/api/runs'; // Using relative path, assuming proxy configuration

    constructor(private http: HttpClient) { }

    getRuns(): Observable<RunResponse[]> {
        return this.http.get<RunResponse[]>(this.apiUrl);
    }

    getRun(id: string): Observable<RunResponse> {
        return this.http.get<RunResponse>(`${this.apiUrl}/${id}`);
    }

    getArtifacts(id: string): Observable<ArtifactResponse[]> {
        return this.http.get<ArtifactResponse[]>(`${this.apiUrl}/${id}/artifacts`);
    }

    createRun(request: RunRequest): Observable<RunResponse> {
        return this.http.post<RunResponse>(this.apiUrl, request);
    }

    getPersonas(): Observable<Persona[]> {
        return this.http.get<Persona[]>('/api/personas');
    }

    chat(personaName: string, message: string): Observable<ChatResponse> {
        return this.http.post<ChatResponse>(`/api/chat/${personaName}`, { message });
    }

    getRunArtifacts(id: string): Observable<ArtifactResponse[]> {
        return this.http.get<ArtifactResponse[]>(`${this.apiUrl}/${id}/artifacts`);
    }

    resolveEscalation(runId: string, decision: string, guidance?: string): Observable<void> {
        return this.http.post<void>(`${this.apiUrl}/${runId}/escalation-decision`, {
            decision,
            guidance: guidance || ''
        });
    }

    getEnvironment(runId: string): Observable<string> {
        return this.http.get(`${this.apiUrl}/${runId}/environment`, { responseType: 'text' });
    }

    resumeRun(runId: string): Observable<RunResponse> {
        return this.http.post<RunResponse>(`${this.apiUrl}/${runId}/resume`, {});
    }

    // A2A endpoints
    getOrchestratorCard(): Observable<AgentCard> {
        return this.http.get<AgentCard>('/.well-known/agent.json');
    }

    listAgents(): Observable<AgentCard[]> {
        return this.http.get<AgentCard[]>('/api/a2a/agents');
    }

    getAgentBindings(): Observable<Record<string, AgentBinding>> {
        return this.http.get<Record<string, AgentBinding>>('/api/a2a/bindings');
    }

    verifyBinding(bindingId: string): Observable<{ binding: AgentBinding; valid: boolean }> {
        return this.http.get<{ binding: AgentBinding; valid: boolean }>(`/api/a2a/bindings/${bindingId}`);
    }

    listCapabilities(): Observable<string[]> {
        return this.http.get<string[]>('/api/a2a/capabilities');
    }

    installAgent(card: AgentCard): Observable<{ success: boolean; message: string; agentName: string }> {
        return this.http.post<{ success: boolean; message: string; agentName: string }>('/api/a2a/agents/install', card);
    }

    // Vis-CoT pipeline mutation endpoints
    flagNode(runId: string, stepId: string, note: string = ''): Observable<void> {
        return this.http.post<void>(`${this.apiUrl}/${runId}/flags`, { stepId, note });
    }

    setPrunedSteps(runId: string, prunedSteps: string): Observable<void> {
        return this.http.put<void>(`${this.apiUrl}/${runId}/pruned-steps`, { prunedSteps });
    }

    addGraft(runId: string, after: string, agentName: string): Observable<void> {
        return this.http.post<void>(`${this.apiUrl}/${runId}/grafts`, { after, agentName });
    }

    // Oversight config
    getOversightConfig(): Observable<OversightConfigPayload> {
        return this.http.get<OversightConfigPayload>('/api/oversight/config');
    }

    saveOversightConfig(config: OversightConfigPayload): Observable<OversightConfigPayload> {
        return this.http.post<OversightConfigPayload>('/api/oversight/config', config);
    }

    // Collaboration endpoints
    getCollaborationEvents(runId: string, limit: number = 50): Observable<CollaborationEventEntity[]> {
        return this.http.get<CollaborationEventEntity[]>(
            `${this.apiUrl}/${runId}/collaboration/events?limit=${limit}`
        );
    }

    getActiveUsers(runId: string): Observable<string[]> {
        return this.http.get<string[]>(`${this.apiUrl}/${runId}/collaboration/users`);
    }

    // Graft management endpoints
    getGraftExecutions(runId?: string, agentName?: string, limit: number = 100): Observable<GraftExecution[]> {
        let params: any = { limit: limit.toString() };
        if (runId) params.runId = runId;
        if (agentName) params.agentName = agentName;
        return this.http.get<GraftExecution[]>('/api/grafts/executions', { params });
    }

    getGraftExecution(id: string): Observable<GraftExecution> {
        return this.http.get<GraftExecution>(`/api/grafts/executions/${id}`);
    }

    getCircuitBreakerStatus(agentName?: string): Observable<CircuitBreakerStatus[]> {
        const params: Record<string, string> = agentName ? { agentName } : {};
        return this.http.get<CircuitBreakerStatus[]>('/api/grafts/circuit-breaker/status', { params });
    }

    resetCircuitBreaker(agentName: string): Observable<void> {
        return this.http.post<void>(`/api/grafts/circuit-breaker/${agentName}/reset`, {});
    }

    getAvailableGraftAgents(): Observable<string[]> {
        return this.http.get<string[]>('/api/grafts/agents');
    }

    getPendingInterrupts(): Observable<PendingInterrupt[]> {
        return this.http.get<PendingInterrupt[]>('/api/oversight/interrupts/pending');
    }

    resolveInterrupt(runId: string, request: InterruptDecisionRequest): Observable<void> {
        return this.http.post<void>(`/api/oversight/runs/${runId}/interrupt-decision`, request);
    }

    // Authentication endpoints
    login(username: string, password: string, deviceInfo?: string): Observable<{ accessToken: string; refreshToken: string; tokenType: string; expiresIn: number }> {
        const body: any = { username, password };
        if (deviceInfo) {
            body.deviceInfo = deviceInfo;
        }
        return this.http.post<{ accessToken: string; refreshToken: string; tokenType: string; expiresIn: number }>('/api/auth/login', body);
    }

    register(username: string, email: string, password: string): Observable<UserRegistrationResponse> {
        const request: UserRegistrationRequest = { username, email, password };
        return this.http.post<UserRegistrationResponse>('/api/auth/register', request);
    }

    getCurrentUser(): Observable<CurrentUserDto> {
        return this.http.get<CurrentUserDto>('/api/auth/me');
    }

    initiatePasswordReset(email: string): Observable<{ message: string; token: string }> {
        return this.http.post<{ message: string; token: string }>('/api/auth/password-reset/initiate', { email });
    }

    completePasswordReset(token: string, newPassword: string): Observable<{ message: string }> {
        return this.http.post<{ message: string }>('/api/auth/password-reset/complete', { token, newPassword });
    }
}
