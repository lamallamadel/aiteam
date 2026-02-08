import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RunRequest, RunResponse, ArtifactResponse, Persona, ChatResponse } from '../models/orchestrator.model';

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
}
