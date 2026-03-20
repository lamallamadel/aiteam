import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { Subject } from 'rxjs';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import { CrdtSyncService } from './crdt-sync.service';
import { CollaborationWebSocketService } from './collaboration-websocket.service';
import { CrdtMessage } from '../models/collaboration.model';

// ─── helpers ──────────────────────────────────────────────────────────────────

function makeMsg(type: CrdtMessage['type'], extra: Partial<CrdtMessage> = {}): CrdtMessage {
  return { type, runId: 'run-1', sourceRegion: 'us-east-1', timestamp: 1000, ...extra };
}

// ─── suite ────────────────────────────────────────────────────────────────────

describe('CrdtSyncService', () => {
  let service: CrdtSyncService;
  let http: HttpTestingController;
  let crdtSubject: Subject<CrdtMessage>;
  let mockCollab: { crdtMessages$: Subject<CrdtMessage>['asObservable'] extends () => infer R ? R : never; sendCrdtSync: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    crdtSubject = new Subject<CrdtMessage>();
    mockCollab = {
      crdtMessages$: crdtSubject.asObservable(),
      sendCrdtSync: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        CrdtSyncService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CollaborationWebSocketService, useValue: mockCollab },
      ],
    });

    service = TestBed.inject(CrdtSyncService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  // ── creation ────────────────────────────────────────────────────────────────

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  // ── crdtUpdates$ ────────────────────────────────────────────────────────────

  it('crdtUpdates$ proxies collaborationService.crdtMessages$', () => {
    const received: CrdtMessage[] = [];
    service.crdtUpdates$.subscribe(m => received.push(m));

    crdtSubject.next(makeMsg('CRDT_UPDATE', { changes: 'abc' }));

    expect(received).toHaveLength(1);
    expect(received[0].type).toBe('CRDT_UPDATE');
    expect(received[0].changes).toBe('abc');
  });

  it('crdtUpdates$ forwards all three message types in order', () => {
    const types: CrdtMessage['type'][] = [];
    service.crdtUpdates$.subscribe(m => types.push(m.type));

    crdtSubject.next(makeMsg('CRDT_SYNC'));
    crdtSubject.next(makeMsg('CRDT_UPDATE'));
    crdtSubject.next(makeMsg('CRDT_FULL_SYNC', { state: 'fullstate' }));

    expect(types).toEqual(['CRDT_SYNC', 'CRDT_UPDATE', 'CRDT_FULL_SYNC']);
  });

  // ── sendSync ────────────────────────────────────────────────────────────────

  it('sendSync delegates to collaborationService.sendCrdtSync', () => {
    service.sendSync('base64changes', 42);
    expect(mockCollab.sendCrdtSync).toHaveBeenCalledOnce();
    expect(mockCollab.sendCrdtSync).toHaveBeenCalledWith('base64changes', 42);
  });

  // ── getState ────────────────────────────────────────────────────────────────

  it('getState GET /api/crdt/runs/{runId}/state', () => {
    const runId = 'run-abc';
    let result: any;
    service.getState(runId).subscribe(r => (result = r));

    const req = http.expectOne(`/api/crdt/runs/${runId}/state`);
    expect(req.request.method).toBe('GET');

    const body = { runId, grafts: [], prunedSteps: ['QUALIFIER'], flags: [], region: 'eu-west-1' };
    req.flush(body);

    expect(result).toEqual(body);
  });

  // ── getChanges ──────────────────────────────────────────────────────────────

  it('getChanges GET /api/crdt/runs/{runId}/changes', () => {
    const runId = 'run-xyz';
    let result: any;
    service.getChanges(runId).subscribe(r => (result = r));

    const req = http.expectOne(`/api/crdt/runs/${runId}/changes`);
    expect(req.request.method).toBe('GET');

    req.flush({ runId, changes: 'encodedBytes==', region: 'us-east-1' });

    expect(result.changes).toBe('encodedBytes==');
  });

  // ── syncChanges ─────────────────────────────────────────────────────────────

  it('syncChanges POST /api/crdt/runs/{runId}/sync with correct body', () => {
    const runId = 'run-abc';
    let result: any;
    service.syncChanges(runId, 'base64payload', 999).subscribe(r => (result = r));

    const req = http.expectOne(`/api/crdt/runs/${runId}/sync`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      changes: 'base64payload',
      sourceRegion: 'browser',
      lamportTimestamp: 999,
    });

    req.flush({ success: true, region: 'us-east-1' });
    expect(result.success).toBe(true);
  });

  // ── registerPeer ────────────────────────────────────────────────────────────

  it('registerPeer POST /api/crdt/runs/{runId}/register-peer', () => {
    const runId = 'run-abc';
    let result: any;
    service.registerPeer(runId, 'eu-west-1').subscribe(r => (result = r));

    const req = http.expectOne(`/api/crdt/runs/${runId}/register-peer`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ peerRegion: 'eu-west-1' });

    req.flush({ success: true, peers: ['eu-west-1'] });
    expect(result.peers).toContain('eu-west-1');
  });

  // ── getPeers ────────────────────────────────────────────────────────────────

  it('getPeers GET /api/crdt/runs/{runId}/peers', () => {
    const runId = 'run-abc';
    let result: any;
    service.getPeers(runId).subscribe(r => (result = r));

    const req = http.expectOne(`/api/crdt/runs/${runId}/peers`);
    expect(req.request.method).toBe('GET');

    req.flush({ runId, peers: ['us-east-1', 'eu-west-1'], region: 'ap-southeast-1' });
    expect(result.peers).toHaveLength(2);
  });

  // ── getMeshStatus ───────────────────────────────────────────────────────────

  it('getMeshStatus GET /api/crdt/mesh/status', () => {
    let result: any;
    service.getMeshStatus().subscribe(r => (result = r));

    const req = http.expectOne('/api/crdt/mesh/status');
    expect(req.request.method).toBe('GET');

    req.flush({ region: 'us-east-1', status: 'active' });
    expect(result.status).toBe('active');
  });
});
