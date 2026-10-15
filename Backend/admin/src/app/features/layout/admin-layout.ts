import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CommunityService } from '../../core/services/community.service';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="layout-container">
      <!-- SIDEBAR DE NAVEGACIÓN -->
      <aside class="sidebar">
        <div class="brand">
          <span class="brand-icon">🎬</span>
          <div class="brand-text">
            <h2>Audiocinemateca</h2>
            <span class="brand-badge">ADMIN CONSOLE</span>
          </div>
        </div>

        <nav class="nav-menu">
          <a
            routerLink="/dashboard"
            routerLinkActive="active"
            class="nav-item"
            title="Dashboard general y métricas"
          >
            <span class="nav-icon">📊</span>
            <span>Dashboard</span>
          </a>

          <a
            routerLink="/catalog"
            routerLinkActive="active"
            class="nav-item"
            title="Catálogo cinematográfico y ficha exclusiva"
          >
            <span class="nav-icon">🎬</span>
            <span>Catálogo Exclusivo</span>
          </a>

          <a
            routerLink="/users"
            routerLinkActive="active"
            class="nav-item"
            title="Gestión de usuarios y moderación de sanciones"
          >
            <span class="nav-icon">👥</span>
            <span>Usuarios & Sanciones</span>
          </a>

          <a
            routerLink="/community"
            routerLinkActive="active"
            class="nav-item"
            title="Chat global en tiempo real y comunicados oficiales"
          >
            <span class="nav-icon">💬</span>
            <span>Chat & Anuncios</span>
          </a>

          <a
            routerLink="/config"
            routerLinkActive="active"
            class="nav-item"
            title="Control de versiones de la app y Remote Config"
          >
            <span class="nav-icon">⚙️</span>
            <span>Configuración & APK</span>
          </a>

          <a
            routerLink="/agents"
            routerLinkActive="active"
            class="nav-item"
            title="Aura IA y automatización de tareas en segundo plano"
          >
            <span class="nav-icon">🤖</span>
            <span>Agentes & Aura IA</span>
          </a>
        </nav>

        <div class="sidebar-footer">
          <div class="admin-profile">
            <div class="avatar">{{ userInitial() }}</div>
            <div class="profile-info">
              <span class="profile-name">{{ auth.currentUser()?.displayName || auth.currentUser()?.username }}</span>
              <span class="profile-role badge badge-primary">{{ auth.currentUser()?.role }}</span>
            </div>
          </div>
          <button class="btn btn-secondary btn-sm btn-logout" (click)="auth.logout()" title="Cerrar sesión">
            🚪 Salir
          </button>
        </div>
      </aside>

      <!-- CONTENIDO PRINCIPAL -->
      <div class="main-content">
        <!-- TOPBAR SUPERIOR -->
        <header class="topbar">
          <div class="topbar-left">
            <span class="site-tag">audiocinemateca.johanag.dev</span>
          </div>

          <div class="topbar-right">
            <!-- ESTADO DE WEBSOCKETS EN VIVO -->
            <div class="live-pill" [class.connected]="community.isConnected()">
              <span class="pulse-dot"></span>
              <span>{{ community.isConnected() ? 'WebSockets En Vivo' : 'Reconectando Socket...' }}</span>
            </div>

            <!-- USUARIOS ACTIVOS EN VIVO -->
            <div class="badge badge-success online-counter" title="Usuarios actualmente conectados a la plataforma">
              👥 {{ community.onlineUsers().length }} en línea
            </div>
          </div>
        </header>

        <!-- ÁREA DE VISTAS HIJAS -->
        <main class="page-body">
          <router-outlet />
        </main>
      </div>
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
      width: 270px;
      min-width: 270px;
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
      padding: 6px 10px;
    }

    /* --- CONTENIDO PRINCIPAL --- */
    .main-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    .topbar {
      height: 64px;
      background-color: var(--bg-sidebar);
      border-bottom: 1px solid var(--border-color);
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 28px;
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

    .page-body {
      flex: 1;
      overflow-y: auto;
      padding: 28px;
    }
  `],
})
export class AdminLayoutComponent implements OnInit, OnDestroy {
  readonly auth = inject(AuthService);
  readonly community = inject(CommunityService);

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
