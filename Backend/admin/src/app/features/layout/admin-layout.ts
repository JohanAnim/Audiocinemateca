import { Component, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CommunityService } from '../../core/services/community.service';
import { AuraAgentService } from '../../core/services/aura-agent.service';
import { AuraChatPanelComponent } from '../agents/aura-chat-panel';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [CommonModule, RouterModule, AuraChatPanelComponent],
  template: `
    <a href="#main-content" class="skip-link">
      Saltar al contenido principal
    </a>

    <div class="layout-container">
      <aside class="sidebar" role="navigation" aria-label="Navegación principal">
        <div class="brand">
          <div class="brand-text">
            <h2>Audiocinemateca</h2>
            <span class="brand-badge">ADMINISTRACIÓN</span>
          </div>
        </div>

        <nav class="nav-menu" aria-label="Secciones">
          <a routerLink="/dashboard" routerLinkActive="active" class="nav-item">
            <span>Resumen</span>
          </a>

          <a routerLink="/catalog" routerLinkActive="active" class="nav-item">
            <span>Catálogo</span>
          </a>

          <a routerLink="/users" routerLinkActive="active" class="nav-item">
            <span>Usuarios</span>
          </a>

          <a routerLink="/community" routerLinkActive="active" class="nav-item">
            <span>Comunidad</span>
          </a>

          <a routerLink="/config" routerLinkActive="active" class="nav-item">
            <span>Configuración</span>
          </a>

          <a routerLink="/agents" routerLinkActive="active" class="nav-item">
            <span>Aura IA</span>
          </a>

          <a routerLink="/aura-chat" routerLinkActive="active" class="nav-item">
            <span>Chat</span>
          </a>
        </nav>

        <div class="sidebar-footer">
          <div class="admin-profile">
            <div class="avatar" aria-hidden="true">{{ userInitial() }}</div>
            <div class="profile-info">
              <span class="profile-name">
                {{ auth.currentUser()?.displayName || auth.currentUser()?.username }}
              </span>
              <span class="profile-role badge badge-primary">{{ auth.currentUser()?.role }}</span>
            </div>
          </div>
          <button
            class="btn btn-secondary btn-sm btn-logout cursor-pointer"
            (click)="auth.logout()"
          >
            Salir
          </button>
        </div>
      </aside>

      <div class="main-content">
        <header class="topbar" role="banner">
          <div class="topbar-left">
            <span class="site-tag">Panel de Control</span>
          </div>

          <div class="topbar-right" role="status" aria-live="polite">
            <button
              type="button"
              class="btn btn-primary btn-sm chat-nav-btn cursor-pointer"
              (click)="auraAgent.toggleChat()"
              [attr.aria-expanded]="auraAgent.isOpen()"
              aria-label="Abrir chat con Aura"
              title="Abrir chat con Aura"
            >
              <span>💬 Chat</span>
            </button>

            <div
              class="live-pill"
              [class.connected]="community.isConnected()"
            >
              <span class="pulse-dot" aria-hidden="true"></span>
              <span>{{ community.isConnected() ? 'Conectado' : 'Reconectando...' }}</span>
            </div>

            <div class="badge badge-success online-counter">
              <span>{{ community.onlineUsers().length }} en línea</span>
            </div>
          </div>
        </header>

        <div class="scroll-wrapper">
          <main id="main-content" class="page-body" role="main" tabindex="-1">
            <router-outlet />
          </main>

          <footer class="admin-footer" role="contentinfo">
            <div class="footer-content">
              <p class="copyright-text">
                © {{ currentYear }} <strong>Audiocinemateca</strong>
              </p>
            </div>
          </footer>
        </div>
      </div>

      <!-- DRAWER FLOTANTE DE CHAT CON AURA -->
      @if (auraAgent.isOpen()) {
        <div class="chat-drawer-backdrop" (click)="auraAgent.closeChat()" role="presentation">
          <aside
            class="chat-drawer"
            (click)="$event.stopPropagation()"
            role="dialog"
            aria-modal="true"
            aria-label="Chat con Aura"
          >
            <div class="drawer-header">
              <div style="display: flex; align-items: center; gap: 8px;">
                <span style="font-size: 16px;">💬</span>
                <strong>Chat con Aura</strong>
              </div>
              <button
                type="button"
                class="btn btn-secondary btn-sm cursor-pointer"
                (click)="auraAgent.closeChat()"
                aria-label="Cerrar ventana de chat"
              >
                ✕
              </button>
            </div>
            <div class="drawer-body">
              <app-aura-chat-panel />
            </div>
          </aside>
        </div>
      }
    </div>
  `,
  styles: [`
    .layout-container {
      display: flex;
      height: 100vh;
      overflow: hidden;
      background-color: var(--bg-app);
    }

    /* --- SIDEBAR --- */
    .sidebar {
      width: 280px;
      min-width: 280px;
      background-color: var(--bg-sidebar);
      border-right: 1px solid var(--border-color);
      display: flex;
      flex-direction: column;
      z-index: 10;
    }

    .brand {
      padding: 24px 20px;
      display: flex;
      align-items: center;
      gap: 12px;
      border-bottom: 1px solid var(--border-color);
    }

    .brand-icon {
      font-size: 28px;
    }

    .brand-text h2 {
      font-size: 16px;
      font-weight: 800;
      color: var(--text-main);
      line-height: 1.2;
    }

    .brand-badge {
      font-size: 10px;
      font-weight: 800;
      color: var(--primary);
      letter-spacing: 1px;
    }

    .nav-menu {
      flex: 1;
      padding: 20px 12px;
      display: flex;
      flex-direction: column;
      gap: 6px;
      overflow-y: auto;
    }

    .nav-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      border-radius: var(--radius-sm);
      font-size: 14px;
      font-weight: 600;
      color: var(--text-muted);
      transition: all 0.2s;
    }

    .nav-item:hover {
      background-color: rgba(255, 255, 255, 0.05);
      color: var(--text-main);
    }

    .nav-item.active {
      background-color: var(--primary);
      color: #ffffff;
      box-shadow: 0 4px 12px rgba(99, 102, 241, 0.35);
    }

    .nav-icon {
      font-size: 18px;
    }

    .sidebar-footer {
      padding: 18px;
      border-top: 1px solid var(--border-color);
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      background-color: rgba(0, 0, 0, 0.2);
    }

    .admin-profile {
      display: flex;
      align-items: center;
      gap: 10px;
      overflow: hidden;
    }

    .avatar {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: linear-gradient(135deg, #6366f1, #8b5cf6);
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 800;
      color: #fff;
      font-size: 14px;
      flex-shrink: 0;
    }

    .profile-info {
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    .profile-name {
      font-size: 13px;
      font-weight: 700;
      color: var(--text-main);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .profile-role {
      font-size: 10px;
      padding: 1px 6px;
      width: fit-content;
    }

    .btn-logout {
      padding: 6px 12px;
    }

    /* --- CONTENIDO PRINCIPAL --- */
    .main-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      min-width: 0;
    }

    .topbar {
      height: 64px;
      background-color: var(--bg-sidebar);
      border-bottom: 1px solid var(--border-color);
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 28px;
      flex-shrink: 0;
    }

    .topbar-left {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .site-tag {
      font-size: 13px;
      color: var(--text-dim);
      font-family: monospace;
      background-color: rgba(255, 255, 255, 0.04);
      padding: 4px 10px;
      border-radius: 4px;
      border: 1px solid var(--border-color);
    }

    .access-pill {
      font-size: 11px;
      font-weight: 700;
      color: #38bdf8;
      background-color: rgba(56, 189, 248, 0.1);
      border: 1px solid rgba(56, 189, 248, 0.25);
      padding: 3px 8px;
      border-radius: 20px;
    }

    .topbar-right {
      display: flex;
      align-items: center;
      gap: 14px;
    }

    .live-pill {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 12px;
      font-weight: 700;
      padding: 6px 12px;
      border-radius: 20px;
      background: rgba(239, 68, 68, 0.15);
      color: #f87171;
      border: 1px solid rgba(239, 68, 68, 0.3);
      transition: all 0.3s;
    }

    .live-pill.connected {
      background: rgba(16, 185, 129, 0.15);
      color: #34d399;
      border-color: rgba(16, 185, 129, 0.3);
    }

    .pulse-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background-color: currentColor;
      box-shadow: 0 0 8px currentColor;
    }

    .online-counter {
      font-size: 12px;
      padding: 6px 12px;
    }

    .scroll-wrapper {
      flex: 1;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
    }

    .page-body {
      flex: 1;
      padding: 28px;
      outline: none;
    }

    /* --- FOOTER ACCESIBLE Y LIMPIO --- */
    .admin-footer {
      background-color: var(--bg-card);
      border-top: 1px solid var(--border-color);
      padding: 16px 28px;
      color: var(--text-muted);
      font-size: 13px;
      margin-top: auto;
    }

    .footer-content {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 16px;
    }

    .copyright-text {
      color: var(--text-dim);
      font-size: 12px;
    }

    .copyright-text strong {
      color: var(--text-main);
    }

    .footer-links {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .footer-status-indicator {
      display: flex;
      align-items: center;
      gap: 6px;
      color: #34d399;
      font-weight: 600;
      font-size: 12px;
    }

    .status-indicator-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background-color: #10b981;
      box-shadow: 0 0 6px #10b981;
    }

    .chat-nav-btn {
      font-weight: 700;
      padding: 6px 14px;
    }

    /* --- DRAWER FLOTANTE --- */
    .chat-drawer-backdrop {
      position: fixed;
      top: 0;
      left: 0;
      width: 100vw;
      height: 100vh;
      background: rgba(0, 0, 0, 0.6);
      backdrop-filter: blur(2px);
      z-index: 1000;
      display: flex;
      justify-content: flex-end;
    }

    .chat-drawer {
      width: 500px;
      max-width: 95vw;
      height: 100vh;
      background-color: var(--bg-card);
      border-left: 1px solid var(--border-color);
      display: flex;
      flex-direction: column;
      box-shadow: -8px 0 24px rgba(0, 0, 0, 0.5);
      animation: slideIn 0.25s ease-out;
    }

    @keyframes slideIn {
      from { transform: translateX(100%); }
      to { transform: translateX(0); }
    }

    .drawer-header {
      padding: 14px 20px;
      border-bottom: 1px solid var(--border-color);
      display: flex;
      align-items: center;
      justify-content: space-between;
      background-color: var(--bg-sidebar);
    }

    .drawer-body {
      flex: 1;
      overflow: hidden;
      display: flex;
      flex-direction: column;
    }

    .drawer-body app-aura-chat-panel {
      flex: 1;
      height: 100%;
      display: flex;
      flex-direction: column;
    }
  `],
})
export class AdminLayoutComponent implements OnInit, OnDestroy {
  readonly auth = inject(AuthService);
  readonly community = inject(CommunityService);
  readonly auraAgent = inject(AuraAgentService);

  readonly currentYear = new Date().getFullYear();

  ngOnInit() {
    this.community.initSocket();
  }

  ngOnDestroy() {
    this.community.disconnectSocket();
  }

  userInitial(): string {
    const name = this.auth.currentUser()?.displayName || this.auth.currentUser()?.username || 'A';
    return name.charAt(0).toUpperCase();
  }
}
