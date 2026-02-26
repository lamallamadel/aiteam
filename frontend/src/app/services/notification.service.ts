import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface NotificationConfig {
  id: string;
  userId: string;
  provider: 'slack' | 'discord';
  webhookUrl: string;
  enabledEvents: string[];
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateNotificationConfig {
  provider: 'slack' | 'discord';
  webhookUrl: string;
  enabledEvents: string[];
}

export interface UpdateNotificationConfig {
  webhookUrl?: string;
  enabledEvents?: string[];
  enabled?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private readonly API_BASE = '/api/notifications';

  constructor(private http: HttpClient) {}

  getConfigs(): Observable<NotificationConfig[]> {
    return this.http.get<NotificationConfig[]>(`${this.API_BASE}/configs`);
  }

  createConfig(config: CreateNotificationConfig): Observable<NotificationConfig> {
    return this.http.post<NotificationConfig>(`${this.API_BASE}/configs`, config);
  }

  updateConfig(configId: string, update: UpdateNotificationConfig): Observable<NotificationConfig> {
    return this.http.put<NotificationConfig>(`${this.API_BASE}/configs/${configId}`, update);
  }

  deleteConfig(configId: string): Observable<void> {
    return this.http.delete<void>(`${this.API_BASE}/configs/${configId}`);
  }
}
