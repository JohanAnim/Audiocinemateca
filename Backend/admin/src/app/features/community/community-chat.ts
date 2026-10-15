import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CommunityService } from '../../core/services/community.service';
import { ChatMessage, Announcement } from '../../core/models/community.models';

@Component({
  selector: 'app-community-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="community-page">
      <div class="page-header">
        <div>
          <h1>Chat Global en Tiempo Real & Anuncios</h1>
          <p class="subtitle">Monitoreo en vivo de conversaciones, moderación de mensajes y comunicados oficiales</p>
        </div>
      </div>

      <div class="community-grid">
        <!-- SECCIÓN IZQUIERDA: CHAT EN VIVO -->
        <div class="card chat-card">
          <div class="chat-header">
            <div class="chat-header-title">
              <h3>💬 Transmisión en Vivo del Chat</h3>
              <span class="live-tag" [class.connected]="community.isConnected()">
                <span class="pulse-dot"></span>
                {{ community.isConnected() ? 'Conectado a /ws/chat' : 'Reconectando...' }}
              </span>
            </div>
            <button class="btn btn-secondary btn-sm" (click)="loadHistory()">
              🔄 Recargar
            </button>
          </div>

          <!-- LISTA DE MENSAJES -->
          <div class="messages-container" #messagesList>
            @if (community.messages().length === 0) {
              <div class="empty-messages">
                <p>No hay mensajes recientes en el chat global.</p>
              </div>
            }

            @for (msg of community.messages(); track msg.id) {
              <div class="message-item" [class.admin-msg]="msg.user?.role === 'ADMIN'">
                <div class="msg-avatar">
                  {{ (msg.user?.displayName || msg.user?.username || 'U').charAt(0).toUpperCase() }}
                </div>
                <div class="msg-body">
                  <div class="msg-meta">
                    <strong class="msg-author">{{ msg.user?.displayName || msg.user?.username }}</strong>
                    @if (msg.user?.role === 'ADMIN') {
                      <span class="badge badge-danger">ADMIN</span>
                    }
                    <small class="msg-time">{{ msg.createdAt | date:'shortTime' }}</small>
                  </div>
                  <div class="msg-text">{{ msg.content }}</div>
                </div>
                <button
                  class="btn-delete-msg"
                  (click)="deleteMessage(msg.id)"
                  title="Eliminar este mensaje para todos en tiempo real"
                >
                  🗑️
                </button>
              </div>
            }
          </div>

          <!-- ENTRADA DE MENSAJE ADMINISTRATIVO -->
          <form class="chat-input-bar" (ngSubmit)="sendAdminMessage()">
            <input
              type="text"
              class="form-control"
              placeholder="Escribe un mensaje como Administrador en el chat global..."
              [(ngModel)]="outgoingMessage"
              name="outgoing"
            />
            <button type="submit" class="btn btn-primary" [disabled]="!outgoingMessage.trim()">
              Enviar
            </button>
          </form>
        </div>

        <!-- SECCIÓN DERECHA: USUARIOS CONECTADOS & ANUNCIOS OFICIALES -->
        <div class="side-panels">
          <!-- USUARIOS EN LÍNEA EN VIVO -->
          <div class="card users-online-card">
            <div class="side-head">
              <h4>👥 Conectados Ahora ({{ community.onlineUsers().length }})</h4>
              <span class="badge badge-success">En Vivo</span>
            </div>

            <div class="online-users-list">
              @if (community.onlineUsers().length === 0) {
                <small class="no-users">Esperando conexiones activas...</small>
              }

              @for (u of community.onlineUsers(); track u.id) {
                <div class="online-user-item">
                  <span class="dot online"></span>
                  <div class="u-info">
                    <span class="u-name">{{ u.displayName || u.username }}</span>
                    <small class="u-device">📱 {{ u.deviceInfo || 'Android' }}</small>
                    @if (u.currentlyListening) {
                      <small class="u-listening">🎧 {{ u.currentlyListening }}</small>
                    }
                  </div>
                </div>
              }
            </div>
          </div>

          <!-- GESTOR DE COMUNICADOS Y ANUNCIOS -->
          <div class="card announcements-card">
            <div class="side-head">
              <h4>📢 Publicar Comunicado</h4>
            </div>

            <div class="form-group mt-2">
              <label class="form-label">Título del Comunicado</label>
              <input
                type="text"
                class="form-control"
                placeholder="Ej. Nueva Versión Disponible"
                [(ngModel)]="announcementTitle"
              />
            </div>

            <div class="form-group">
              <label class="form-label">Contenido del Mensaje *</label>
              <textarea
                class="form-control"
                rows="3"
                placeholder="Escribe el aviso que se desplegará en la app para todos los usuarios..."
                [(ngModel)]="announcementContent"
              ></textarea>
            </div>

            <button
              class="btn btn-primary btn-block"
              (click)="publishAnnouncement()"
              [disabled]="!announcementContent.trim() || isPublishing()"
            >
              @if (isPublishing()) {
                <span>Publicando...</span>
              } @else {
                <span>📢 Emitir Anuncio Oficial</span>
              }
            </button>

            <!-- LISTA DE ANUNCIOS RECIENTES -->
            <div class="announcements-history mt-3">
              <span class="form-label">Anuncios Activos</span>
              @for (ann of announcements(); track ann.id) {
                <div class="announcement-item">
                  <div class="ann-info">
                    <strong>{{ ann.title || 'Aviso Oficial' }}</strong>
                    <p>{{ ann.content }}</p>
                    <small>{{ ann.createdAt | date:'short' }}</small>
                  </div>
                  <button class="btn btn-danger btn-sm" (click)="deleteAnnouncement(ann.id)">✕</button>
                </div>
              }
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .community-page {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .page-header h1 {
      font-size: 24px;
      font-weight: 800;
    }

    .subtitle {
      font-size: 14px;
      color: var(--text-muted);
    }

    .community-grid {
      display: grid;
      grid-template-columns: 1fr 380px;
      gap: 20px;
    }

    @media (max-width: 1024px) {
      .community-grid {
        grid-template-columns: 1fr;
      }
    }

    /* --- CHAT CARD --- */
    .chat-card {
      display: flex;
      flex-direction: column;
      height: calc(100vh - 180px);
      padding: 0;
      overflow: hidden;
    }

    .chat-header {
      padding: 16px 20px;
      border-bottom: 1px solid var(--border-color);
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .chat-header-title {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .chat-header-title h3 {
      font-size: 16px;
      font-weight: 700;
    }

    .live-tag {
      font-size: 11px;
      font-weight: 700;
      padding: 3px 8px;
      border-radius: 12px;
      background: rgba(239, 68, 68, 0.15);
      color: #f87171;
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .live-tag.connected {
      background: rgba(16, 185, 129, 0.15);
      color: #34d399;
    }

    .pulse-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background-color: currentColor;
    }

    .messages-container {
      flex: 1;
      overflow-y: auto;
      padding: 20px;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .empty-messages {
      text-align: center;
      color: var(--text-muted);
      margin: auto;
      font-size: 14px;
    }

    .message-item {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 10px 14px;
      border-radius: var(--radius-sm);
      background-color: var(--bg-input);
      border: 1px solid var(--border-color);
      transition: background-color 0.2s;
    }

    .message-item:hover {
      background-color: var(--bg-card-hover);
    }

    .message-item.admin-msg {
      border-left: 3px solid var(--danger);
    }

    .msg-avatar {
      width: 34px;
      height: 34px;
      border-radius: 50%;
      background: linear-gradient(135deg, #6366f1, #a855f7);
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 800;
      font-size: 13px;
      flex-shrink: 0;
    }

    .msg-body {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .msg-meta {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .msg-author {
      font-size: 13px;
      color: var(--text-main);
    }

    .msg-time {
      font-size: 11px;
      color: var(--text-dim);
    }

    .msg-text {
      font-size: 14px;
      color: var(--text-muted);
      line-height: 1.4;
      word-break: break-word;
    }

    .btn-delete-msg {
      background: none;
      border: none;
      opacity: 0.4;
      cursor: pointer;
      font-size: 14px;
      transition: opacity 0.2s;
    }

    .btn-delete-msg:hover {
      opacity: 1;
    }

    .chat-input-bar {
      padding: 14px 20px;
      border-top: 1px solid var(--border-color);
      display: flex;
      gap: 10px;
      background-color: rgba(0, 0, 0, 0.2);
    }

    /* --- SIDE PANELS --- */
    .side-panels {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .side-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;
      padding-bottom: 8px;
      border-bottom: 1px solid var(--border-color);
    }

    .side-head h4 {
      font-size: 14px;
      font-weight: 700;
    }

    .online-users-list {
      max-height: 180px;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .online-user-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 6px 10px;
      background-color: var(--bg-input);
      border-radius: var(--radius-sm);
      font-size: 13px;
    }

    .dot.online {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background-color: #10b981;
      box-shadow: 0 0 6px #10b981;
    }

    .u-info {
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    .u-name {
      font-weight: 600;
      color: var(--text-main);
    }

    .u-device {
      font-size: 11px;
      color: var(--text-dim);
    }

    .u-listening {
      font-size: 11px;
      color: #818cf8;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .announcements-history {
      display: flex;
      flex-direction: column;
      gap: 8px;
      max-height: 200px;
      overflow-y: auto;
    }

    .announcement-item {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 8px;
      padding: 10px;
      background-color: var(--bg-input);
      border-radius: var(--radius-sm);
      border: 1px solid var(--border-color);
      font-size: 12px;
    }

    .ann-info strong {
      color: var(--text-main);
    }

    .ann-info p {
      color: var(--text-muted);
      margin: 2px 0;
    }

    .ann-info small {
      color: var(--text-dim);
    }

    .btn-block {
      width: 100%;
    }
  `],
})
export class CommunityChatComponent implements OnInit {
  readonly community = inject(CommunityService);

  outgoingMessage = '';
  announcementTitle = '';
  announcementContent = '';
  isPublishing = signal<boolean>(false);
  announcements = signal<Announcement[]>([]);

  ngOnInit() {
    this.community.initSocket();
    this.loadHistory();
    this.loadAnnouncements();
  }

  loadHistory() {
    this.community.getChatHistory(1, 50).subscribe({
      next: (res) => {
        this.community.messages.set(res.data || []);
      },
    });
  }

  loadAnnouncements() {
    this.community.getAnnouncements().subscribe({
      next: (list) => {
        this.announcements.set(list);
      },
    });
  }

  sendAdminMessage() {
    if (!this.outgoingMessage.trim()) return;
    this.community.sendMessage(this.outgoingMessage.trim());
    this.outgoingMessage = '';
  }

  deleteMessage(messageId: string) {
    if (confirm('¿Eliminar este mensaje del chat global para todos los usuarios?')) {
      this.community.deleteChatMessage(messageId).subscribe();
    }
  }

  publishAnnouncement() {
    if (!this.announcementContent.trim()) return;

    this.isPublishing.set(true);
    this.community
      .createAnnouncement(this.announcementContent.trim(), this.announcementTitle.trim() || undefined)
      .subscribe({
        next: () => {
          this.isPublishing.set(false);
          this.announcementTitle = '';
          this.announcementContent = '';
          alert('¡Comunicado oficial publicado con éxito!');
          this.loadAnnouncements();
        },
        error: (err) => {
          this.isPublishing.set(false);
          alert(`Error al publicar: ${err?.message || 'Error desconocido'}`);
        },
      });
  }

  deleteAnnouncement(id: string) {
    if (confirm('¿Eliminar este comunicado oficial?')) {
      this.community.deleteAnnouncement(id).subscribe({
        next: () => {
          this.loadAnnouncements();
        },
      });
    }
  }
}
