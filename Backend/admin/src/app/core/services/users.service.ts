import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { UsersPaginatedResponse, UserListItem, BanUserDto } from '../models/user.models';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class UsersService {
  private readonly api = inject(ApiService);

  getUsers(params?: {
    page?: number;
    limit?: number;
    search?: string;
    role?: string;
    isOnline?: boolean;
  }): Observable<UsersPaginatedResponse> {
    return this.api.get<UsersPaginatedResponse>('users', params);
  }

  getUserDetail(id: string): Observable<UserListItem> {
    return this.api.get<UserListItem>(`users/${id}`);
  }

  updateRole(id: string, role: 'USER' | 'MODERATOR' | 'ADMIN'): Observable<any> {
    return this.api.patch(`users/${id}/role`, { role });
  }

  banUser(dto: BanUserDto): Observable<any> {
    return this.api.post('community/ban', dto);
  }

  unbanUser(targetUserId: string): Observable<any> {
    return this.api.post('community/unban', { targetUserId });
  }

  deleteUser(id: string): Observable<any> {
    return this.api.delete(`users/${id}`);
  }
}
