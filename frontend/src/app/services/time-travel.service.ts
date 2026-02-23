import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TimeTravelSnapshot, CollaborationAnalytics } from '../models/collaboration.model';

@Injectable({
  providedIn: 'root'
})
export class TimeTravelService {
  private baseUrl = '/api/runs';

  constructor(private http: HttpClient) {}

  getEventHistory(runId: string, startTimestamp?: number, endTimestamp?: number): Observable<TimeTravelSnapshot[]> {
    let url = `${this.baseUrl}/${runId}/collaboration/history`;
    
    if (startTimestamp && endTimestamp) {
      url += `?startTimestamp=${startTimestamp}&endTimestamp=${endTimestamp}`;
    }
    
    return this.http.get<TimeTravelSnapshot[]>(url);
  }

  getAnalytics(runId: string): Observable<CollaborationAnalytics> {
    return this.http.get<CollaborationAnalytics>(`${this.baseUrl}/${runId}/collaboration/analytics`);
  }

  exportAsJson(runId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${runId}/collaboration/export/json`, {
      responseType: 'blob'
    });
  }

  exportAsCsv(runId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${runId}/collaboration/export/csv`, {
      responseType: 'blob'
    });
  }

  downloadFile(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    window.URL.revokeObjectURL(url);
  }
}
