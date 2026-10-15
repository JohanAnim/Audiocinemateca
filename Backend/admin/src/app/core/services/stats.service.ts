import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { ExecutiveStats } from '../models/stats.models';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class StatsService {
  private readonly api = inject(ApiService);

  getExecutiveStats(): Observable<ExecutiveStats> {
    return this.api.get<ExecutiveStats>('admin/stats');
  }
}
