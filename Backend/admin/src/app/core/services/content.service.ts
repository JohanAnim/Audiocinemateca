import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { ContentsPaginatedResponse, ContentItem, CreateContentDto } from '../models/content.models';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class ContentService {
  private readonly api = inject(ApiService);

  getContents(params?: {
    page?: number;
    limit?: number;
    search?: string;
    type?: string;
    status?: string;
    isExclusive?: string;
    sortBy?: string;
    order?: 'asc' | 'desc';
  }): Observable<ContentsPaginatedResponse> {
    return this.api.get<ContentsPaginatedResponse>('contents', params);
  }

  getContent(id: string): Observable<ContentItem> {
    return this.api.get<ContentItem>(`contents/${id}`);
  }

  createContent(dto: CreateContentDto): Observable<ContentItem> {
    return this.api.post<ContentItem>('contents', dto);
  }

  updateContent(id: string, dto: Partial<CreateContentDto>): Observable<ContentItem> {
    return this.api.put<ContentItem>(`contents/${id}`, dto);
  }

  deleteContent(id: string): Observable<{ success: boolean; message: string }> {
    return this.api.delete<{ success: boolean; message: string }>(`contents/${id}`);
  }

  getCategories(): Observable<{ id: string; name: string; slug: string; contentCount: number }[]> {
    return this.api.get('contents/categories');
  }

  // --- SINCRONIZACIÓN Y ACTUALIZACIÓN DEL CATÁLOGO ---
  getCatalogSyncStatus(): Observable<any> {
    return this.api.get('catalog-sync/status');
  }

  checkRemoteCatalogUpdate(sendPush = false): Observable<any> {
    return this.api.post('catalog-sync/check', { sendPush });
  }

  syncCatalogToDatabase(sendPush = false): Observable<any> {
    return this.api.post('catalog-sync/sync', { sendPush });
  }

  lookupCatalogItem(query: string): Observable<any> {
    return this.api.get('catalog-sync/lookup', { q: query });
  }

  auditCatalogLinks(sampleSize = 25): Observable<any> {
    return this.api.post('catalog-sync/audit-links', { sampleSize });
  }
}
