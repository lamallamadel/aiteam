import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RunRequest, RunResponse, ArtifactResponse, Persona, ChatResponse, AgentCard, AgentBinding } from '../models/orchestrator.model';
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
}
