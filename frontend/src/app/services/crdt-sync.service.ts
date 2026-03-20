import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CollaborationWebSocketService } from './collaboration-websocket.service';
import {
  CrdtDocumentState,
  CrdtMessage,
  CrdtPeersResponse,
} from '../models/collaboration.model';

@Injectable({ providedIn: 'root' })
export class CrdtSyncService {
  private readonly baseUrl = '/api/crdt';

  constructor(
    private http: HttpClient,
    private collaborationService: CollaborationWebSocketService,
  ) {}

  /** Emits every CRDT message received from the collaboration STOMP topic. */
  get crdtUpdates$(): Observable<CrdtMessage> {
    return this.collaborationService.crdtMessages$;
  }

  /** Send local CRDT changes to the server via STOMP. */
  sendSync(changes: string, lamportTimestamp: number): void {
    this.collaborationService.sendCrdtSync(changes, lamportTimestamp);
  }

  /** GET /api/crdt/runs/{runId}/state — current merged CRDT document state. */
  getState(runId: string): Observable<CrdtDocumentState> {
    return this.http.get<CrdtDocumentState>(`${this.baseUrl}/runs/${runId}/state`);
  }

  /** GET /api/crdt/runs/{runId}/changes — serialised changes as base64. */
  getChanges(runId: string): Observable<{ runId: string; changes: string; region: string }> {
    return this.http.get<{ runId: string; changes: string; region: string }>(
      `${this.baseUrl}/runs/${runId}/changes`,
    );
  }

  /** POST /api/crdt/runs/{runId}/sync — push local changes to server (REST fallback). */
  syncChanges(
    runId: string,
    changes: string,
    lamportTimestamp: number,
  ): Observable<{ success: boolean; region: string }> {
    return this.http.post<{ success: boolean; region: string }>(
      `${this.baseUrl}/runs/${runId}/sync`,
      { changes, sourceRegion: 'browser', lamportTimestamp },
    );
  }

  /** POST /api/crdt/runs/{runId}/register-peer — register a regional peer. */
  registerPeer(
    runId: string,
    peerRegion: string,
  ): Observable<{ success: boolean; peers: string[] }> {
    return this.http.post<{ success: boolean; peers: string[] }>(
      `${this.baseUrl}/runs/${runId}/register-peer`,
      { peerRegion },
    );
  }

  /** GET /api/crdt/runs/{runId}/peers — list registered peers. */
  getPeers(runId: string): Observable<CrdtPeersResponse> {
    return this.http.get<CrdtPeersResponse>(`${this.baseUrl}/runs/${runId}/peers`);
  }

  /** GET /api/crdt/mesh/status — mesh connectivity status. */
  getMeshStatus(): Observable<{ region: string; status: string }> {
    return this.http.get<{ region: string; status: string }>(`${this.baseUrl}/mesh/status`);
  }
}
