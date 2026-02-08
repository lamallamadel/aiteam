import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AnalyticsSummary {
    totalRuns: number;
    successRate: number;
    failureRate: number;
    escalationRate: number;
    statusBreakdown: Record<string, number>;
}

@Injectable({
    providedIn: 'root'
})
export class AnalyticsService {
    private http = inject(HttpClient);
    private apiUrl = '/api/analytics';

    getSummary(): Observable<AnalyticsSummary> {
        return this.http.get<AnalyticsSummary>(`${this.apiUrl}/runs/summary`);
    }

    getEscalationInsights(): Observable<any> {
        return this.http.get(`${this.apiUrl}/escalations/insights`);
    }
}
