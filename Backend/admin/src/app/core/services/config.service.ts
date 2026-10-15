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

  updateConfig(key: string, value: any, description?: string): Observable<AppConfigItem> {
    return this.api.put<AppConfigItem>(`config/${key}`, { value, description });
  }

  deleteConfig(key: string): Observable<any> {
    return this.api.delete(`config/${key}`);
  }

  seedDefaults(): Observable<any> {
    return this.api.post('config/seed', {});
  }
}
