import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { WorkflowEvent } from '../models/orchestrator.model';

@Injectable({ providedIn: 'root' })
export class SseService {
  private eventSource: EventSource | null = null;

  connectToRun(runId: string): Observable<WorkflowEvent> {
    return new Observable(subscriber => {
      this.disconnect();

      const token = localStorage.getItem('orchestrator_token') || '';
      const url = `/api/runs/${runId}/stream?token=${encodeURIComponent(token)}`;

      this.eventSource = new EventSource(url);

      this.eventSource.onmessage = (event: MessageEvent) => {
        try {
          const data: WorkflowEvent = JSON.parse(event.data);
          subscriber.next(data);
        } catch (e) {
          console.warn('Failed to parse SSE event:', event.data);
        }
      };

      // Listen to typed events matching backend event names
      const eventTypes = [
        'STEP_START', 'STEP_COMPLETE', 'TOOL_CALL_START', 'TOOL_CALL_END',
        'WORKFLOW_STATUS', 'LLM_CALL_START', 'LLM_CALL_END',
        'SCHEMA_VALIDATION', 'WORKFLOW_ERROR', 'ESCALATION_RAISED'
      ];

      for (const type of eventTypes) {
        this.eventSource.addEventListener(type, (event: Event) => {
          try {
            const data: WorkflowEvent = JSON.parse((event as MessageEvent).data);
            subscriber.next(data);
          } catch (e) {
            console.warn(`Failed to parse SSE ${type} event`);
          }
        });
      }

      this.eventSource.onerror = () => {
        if (this.eventSource?.readyState === EventSource.CLOSED) {
          subscriber.complete();
        }
      };

      return () => this.disconnect();
    });
  }

  disconnect(): void {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
  }
}
