import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { AppConfigItem } from '../models/config.models';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class ConfigService {
  private readonly api = inject(ApiService);

  getAllConfigs(): Observable<AppConfigItem[]> {
    return this.api.get<AppConfigItem[]>('config/all');
  }

  getPublicAppConfig(): Observable<any> {
    return this.api.get('config/app');
  }

  getGithubReleases(refresh = false): Observable<any> {
    return this.api.get('config/github-releases', refresh ? { refresh: 'true' } : undefined);
  }

  publishBanner(payload: {
    enabled?: boolean;
    id?: string;
    title: string;
    message: string;
    type?: 'info' | 'warning' | 'alert' | 'update';
    dismissible?: boolean;
    actionUrl?: string | null;
    actionText?: string | null;
    sendPush?: boolean;
    optimizePushWithAi?: boolean;
  }): Observable<any> {
    return this.api.post('config/banner/publish', payload);
  }

  updateConfig(key: string, value: any, description?: string): Observable<AppConfigItem> {
    return this.api.put<AppConfigItem>(`config/${key}`, { value, description });
  }

  deleteConfig(key: string): Observable<any> {
    return this.api.delete(`config/${key}`);
  }

  seedDefaults(): Observable<any> {
    return this.api.post('config/seed', {});
  }

  // --- ADMIN: PUSH NOTIFICATIONS & AI COPYWRITER ---
  sendPushNotification(payload: {
    title: string;
    body: string;
    destination?: string;
    url?: string;
    contentType?: string;
    contentId?: string;
    delayMinutes?: number;
    target?: 'all' | 'android' | 'desktop';
  }): Observable<any> {
    return this.api.post('admin/push/send', payload);
  }

  getPushHistory(): Observable<any[]> {
    return this.api.get<any[]>('admin/push/history');
  }

  generatePushWithAi(payload: {
    draftTitle?: string;
    draftMessage?: string;
    contentTitle?: string;
    contentType?: string;
    contextType?: 'banner' | 'push';
  }): Observable<{ pushTitle: string; pushBody: string }> {
    return this.api.post('admin/ai/generate-push', payload);
  }

  // --- ADMIN: COLECCIONES CURADAS IA & RANKING SEMANAL TOP 10 ---
  getCuratedCollections(): Observable<any> {
    return this.api.get('admin/collections');
  }

  regenerateCuratedCollections(): Observable<any> {
    return this.api.post('admin/collections/regenerate', {});
  }

  getWeeklyRanking(): Observable<any> {
    return this.api.get('admin/rankings/weekly');
  }

  recalculateWeeklyRanking(): Observable<any> {
    return this.api.post('admin/rankings/recalculate', {});
  }

  saveManualWeeklyRanking(items: any[]): Observable<any> {
    return this.api.put('admin/rankings/weekly', { items });
  }

  // --- ADMIN: PURGA DE CHAT Y MANTENIMIENTO ---
  purgeChatMessages(payload: { count?: number; olderThanDays?: number }): Observable<any> {
    return this.api.post('admin/chat/purge', payload);
  }

  // --- ADMIN: CEREBRO MULTI-PROVEEDOR DE AURA 2.0 (1. GROQ, 2. GEMINI, 3. PERSONALIZADO) ---
  getAuraBrain(): Observable<any> {
    return this.api.get('admin/ai/brain');
  }

  saveAuraBrain(payload: {
    config: any;
    apiKeys?: {
      groqApiKey?: string;
      geminiApiKey?: string;
      customApiKey?: string;
    };
  }): Observable<any> {
    return this.api.put('admin/ai/brain', payload);
  }

  getAuraBrainModels(provider: 'groq' | 'gemini' | 'custom', customBaseUrl?: string): Observable<any> {
    const params: Record<string, string> = { provider };
    if (customBaseUrl) params['customBaseUrl'] = customBaseUrl;
    return this.api.get('admin/ai/brain/models', params);
  }

  testAuraBrain(payload: {
    prompt?: string;
    overrideConfig?: any;
    tempApiKey?: string;
  }): Observable<any> {
    return this.api.post('admin/ai/brain/test', payload);
  }
}
