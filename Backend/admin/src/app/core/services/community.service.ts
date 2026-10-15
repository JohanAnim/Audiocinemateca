import { Injectable, inject, signal } from '@angular/core';
import { io, Socket } from 'socket.io-client';
import { ApiService } from './api.service';
import { ChatMessage, OnlineUser, Announcement } from '../models/community.models';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class CommunityService {
  private readonly api = inject(ApiService);
  private socket: Socket | null = null;

  readonly isConnected = signal<boolean>(false);
  readonly onlineUsers = signal<OnlineUser[]>([]);
  readonly messages = signal<ChatMessage[]>([]);

  initSocket() {
    if (this.socket || typeof window === 'undefined') return;

    const token = localStorage.getItem('audiocinemateca_admin_token');
    if (!token) return;

    this.socket = io(`${this.api.wsUrl}/ws/chat`, {
      auth: {
        token,
        deviceInfo: 'Panel de Control Web (Administración)',
      },
      transports: ['websocket', 'polling'],
    });

    this.socket.on('connect', () => {
      this.isConnected.set(true);
    });

    this.socket.on('disconnect', () => {
      this.isConnected.set(false);
    });

    this.socket.on('online_users', (data: { count: number; users: OnlineUser[] }) => {
      this.onlineUsers.set(data.users || []);
    });

    this.socket.on('new_message', (msg: ChatMessage) => {
      this.messages.update((list) => [...list, msg]);
    });

    this.socket.on('message_deleted', (data: { messageId: string }) => {
      this.messages.update((list) => list.filter((m) => m.id !== data.messageId));
    });
  }

  disconnectSocket() {
    if (this.socket) {
      this.socket.disconnect();
      this.socket = null;
      this.isConnected.set(false);
    }
  }

  sendMessage(content: string) {
    if (this.socket && this.isConnected()) {
      this.socket.emit('send_message', { content });
    }
  }

  deleteChatMessage(messageId: string) {
    if (this.socket && this.isConnected()) {
      this.socket.emit('delete_message', { messageId });
    }
    return this.api.delete(`community/chat/messages/${messageId}`);
  }

  getChatHistory(page = 1, limit = 50): Observable<{ data: ChatMessage[]; meta: any }> {
    return this.api.get<{ data: ChatMessage[]; meta: any }>('community/chat/messages', {
      page,
      limit,
    });
  }

  getAnnouncements(): Observable<Announcement[]> {
    return this.api.get<Announcement[]>('community/announcements');
  }

  createAnnouncement(content: string, title?: string): Observable<Announcement> {
    return this.api.post<Announcement>('community/announcements', { content, title });
  }

  deleteAnnouncement(id: string): Observable<any> {
    return this.api.delete(`community/announcements/${id}`);
  }
}
