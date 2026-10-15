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
}
