import { Component, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CommunityService } from '../../core/services/community.service';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <!-- ENLACE DIRECTO DE ACCESIBILIDAD PARA LECTORES DE PANTALLA Y TECLADO -->
    <a href="#main-content" class="skip-link">
      Saltar al contenido principal (Skip to content)
    </a>

    <div class="layout-container">
      <!-- SIDEBAR DE NAVEGACIÓN INSTITUCIONAL -->
      <aside class="sidebar" role="navigation" aria-label="Navegación principal de administración">
        <div class="brand">
          <span class="brand-icon" aria-hidden="true">🎬</span>
          <div class="brand-text">
            <h2>Audiocinemateca</h2>
            <span class="brand-badge">CONSOLA ADMINISTRATIVA</span>
          </div>
        </div>

        <nav class="nav-menu" aria-label="Módulos del sistema">
          <a
            routerLink="/dashboard"
            routerLinkActive="active"
            class="nav-item"
            aria-label="Panel general y métricas en tiempo real"
          >
            <span class="nav-icon" aria-hidden="true">📊</span>
            <span>Dashboard & Métricas</span>
          </a>

          <a
            routerLink="/catalog"
            routerLinkActive="active"
            class="nav-item"
            aria-label="Catálogo cinematográfico y creador de fichas exclusivas"
          >
            <span class="nav-icon" aria-hidden="true">🎬</span>
            <span>Catálogo Cinematográfico</span>
          </a>

          <a
            routerLink="/users"
            routerLinkActive="active"
            class="nav-item"
            aria-label="Gestión de usuarios registrados, roles y moderación de sanciones"
          >
            <span class="nav-icon" aria-hidden="true">👥</span>
            <span>Usuarios & Sanciones</span>
          </a>

          <a
            routerLink="/community"
            routerLinkActive="active"
            class="nav-item"
            aria-label="Supervisión de chat comunitario y publicación de comunicados"
          >
            <span class="nav-icon" aria-hidden="true">💬</span>
            <span>Comunidad & Chat en Vivo</span>
          </a>

          <a
            routerLink="/config"
            routerLinkActive="active"
            class="nav-item"
            aria-label="Configuración remota, control de versiones APK y modo mantenimiento"
          >
            <span class="nav-icon" aria-hidden="true">⚙️</span>
            <span>Configuración Remota & APK</span>
          </a>

          <a
            routerLink="/agents"
            routerLinkActive="active"
            class="nav-item"
            aria-label="Agente de inteligencia artificial Aura y automatizaciones programadas"
          >
            <span class="nav-icon" aria-hidden="true">🤖</span>
            <span>Aura IA & Automatizaciones</span>
          </a>
        </nav>

        <div class="sidebar-footer">
          <div class="admin-profile" aria-label="Perfil del administrador activo">
            <div class="avatar" aria-hidden="true">{{ userInitial() }}</div>
            <div class="profile-info">
              <span class="profile-name" [title]="auth.currentUser()?.displayName || auth.currentUser()?.username">
                {{ auth.currentUser()?.displayName || auth.currentUser()?.username }}
              </span>
              <span class="profile-role badge badge-primary" aria-label="Rol asignado">{{ auth.currentUser()?.role }}</span>
            </div>
          </div>
          <button
            class="btn btn-secondary btn-sm btn-logout cursor-pointer"
            (click)="auth.logout()"
            aria-label="Cerrar sesión de forma segura"
            title="Cerrar sesión de administración"
          >
            <span aria-hidden="true">🚪</span>
            <span>Salir</span>
          </button>
        </div>
      </aside>

      <!-- CONTENIDO PRINCIPAL Y HEADER -->
      <div class="main-content">
        <!-- TOPBAR SUPERIOR ACCESIBLE -->
        <header class="topbar" role="banner">
          <div class="topbar-left">
            <span class="site-tag" title="Dominio oficial del panel administrativo">audiocinemateca.johanag.dev</span>
            <span class="access-pill" title="Nivel de conformidad de accesibilidad">
              ♿ WCAG 2.1 AAA
            </span>
          </div>

          <div class="topbar-right" role="status" aria-live="polite">
            <!-- ESTADO DE WEBSOCKETS EN VIVO -->
            <div
              class="live-pill"
              [class.connected]="community.isConnected()"
              [attr.aria-label]="community.isConnected() ? 'Servicio de WebSockets conectado en tiempo real' : 'Reconectando con el servidor WebSockets'"
            >
              <span class="pulse-dot" aria-hidden="true"></span>
              <span>{{ community.isConnected() ? 'Sockets En Vivo' : 'Reconectando...' }}</span>
            </div>

            <!-- USUARIOS ACTIVOS EN VIVO -->
            <div
              class="badge badge-success online-counter"
              [attr.aria-label]="community.onlineUsers().length + ' usuarios conectados en este momento'"
              title="Usuarios actualmente activos en la plataforma"
            >
              <span aria-hidden="true">👥</span>
              <span>{{ community.onlineUsers().length }} en línea</span>
            </div>
          </div>
        </header>

        <!-- ÁREA DE VISTAS HIJAS (LANDMARK MAIN) -->
        <div class="scroll-wrapper">
          <main id="main-content" class="page-body" role="main" tabindex="-1">
            <router-outlet />
          </main>

          <!-- PIE DE PÁGINA INSTITUCIONAL ACCESIBLE -->
          <footer class="admin-footer" role="contentinfo" aria-label="Información institucional y estado del sistema">
            <div class="footer-grid">
              <div class="footer-brand-col">
                <div class="footer-brand">
                  <span class="footer-icon" aria-hidden="true">🎬</span>
                  <span class="footer-title">Audiocinemateca</span>
                  <span class="badge badge-primary">v3.1.0</span>
                </div>
                <p class="footer-desc">
                  Plataforma soberana y accesible de cine, series y documentales con audiodescripción integral en español.
                  Diseñada con estándares rigurosos de inclusión para personas ciegas y con baja visión.
                </p>
                <div class="footer-cert-badges">
                  <span class="cert-pill" title="Cumple con las pautas de accesibilidad para el contenido web nivel AAA">WCAG 2.1 AAA</span>
                  <span class="cert-pill" title="Optimizado para TalkBack, NVDA, JAWS y VoiceOver">Screen Reader First</span>
                  <span class="cert-pill" title="Base de datos relacional PostgreSQL con réplica y cascadas seguras">PostgreSQL 16</span>
                  <span class="cert-pill" title="Backend modular compilado en TypeScript de alto rendimiento">NestJS 12</span>
                </div>
              </div>

              <div class="footer-telemetry-col">
                <h3 class="footer-section-title">Telemetría del Nodo</h3>
                <ul class="telemetry-list" role="list">
                  <li class="telemetry-item">
                    <span class="telemetry-label">Entorno de Producción:</span>
                    <span class="telemetry-value font-mono">Oracle Cloud / Dokploy</span>
                  </li>
                  <li class="telemetry-item">
                    <span class="telemetry-label">Gateway en Tiempo Real:</span>
                    <span class="telemetry-value font-mono text-emerald-400">Socket.IO /ws/chat</span>
                  </li>
                  <li class="telemetry-item">
                    <span class="telemetry-label">Base de Datos:</span>
                    <span class="telemetry-value font-mono">audiocinemateca_db</span>
                  </li>
                  <li class="telemetry-item">
                    <span class="telemetry-label">Reloj Sincronizado:</span>
                    <span class="telemetry-value font-mono">{{ currentTime() }}</span>
                  </li>
                </ul>
              </div>
            </div>

            <div class="footer-bottom-bar">
              <p class="copyright-text">
                © {{ currentYear }} <strong>Audiocinemateca</strong>. Desarrollado por Johan Antonio Gutiérrez con máxima dedicación a la accesibilidad universal.
              </p>
              <div class="footer-links">
                <span class="footer-status-indicator">
                  <span class="status-indicator-dot" aria-hidden="true"></span>
                  Todos los sistemas operativos
                </span>
              </div>
            </div>
          </footer>
        </div>
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

    /* --- FOOTER INSTITUCIONAL ACCESIBLE --- */
    .admin-footer {
      background-color: #0b0f19;
      border-top: 1px solid var(--border-color);
      padding: 32px 28px 24px 28px;
      color: var(--text-muted);
      font-size: 13px;
      margin-top: auto;
    }

    .footer-grid {
      display: grid;
      grid-template-columns: 2fr 1fr;
      gap: 32px;
      margin-bottom: 24px;
    }

    @media (max-width: 900px) {
      .footer-grid {
        grid-template-columns: 1fr;
      }
    }

    .footer-brand {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 10px;
    }

    .footer-icon {
      font-size: 20px;
    }

    .footer-title {
      font-size: 16px;
      font-weight: 800;
      color: var(--text-main);
    }

    .footer-desc {
      font-size: 13px;
      line-height: 1.6;
      color: var(--text-muted);
      max-width: 650px;
      margin-bottom: 14px;
    }

    .footer-cert-badges {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .cert-pill {
      font-size: 11px;
      font-weight: 700;
      padding: 3px 8px;
      border-radius: 6px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      color: var(--text-dim);
    }

    .footer-section-title {
      font-size: 12px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.8px;
      color: var(--text-main);
      margin-bottom: 12px;
    }

    .telemetry-list {
      list-style: none;
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .telemetry-item {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      font-size: 12px;
      padding-bottom: 4px;
      border-bottom: 1px dashed rgba(255, 255, 255, 0.05);
    }

    .telemetry-label {
      color: var(--text-dim);
    }

    .telemetry-value {
      font-weight: 600;
      color: var(--text-muted);
    }

    .footer-bottom-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 16px;
      padding-top: 18px;
      border-top: 1px solid rgba(255, 255, 255, 0.05);
      font-size: 12px;
    }

    .copyright-text {
      color: var(--text-dim);
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
    }

    .status-indicator-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background-color: #10b981;
      box-shadow: 0 0 6px #10b981;
    }
  `],
})
export class AdminLayoutComponent implements OnInit, OnDestroy {
  readonly auth = inject(AuthService);
  readonly community = inject(CommunityService);

  readonly currentYear = new Date().getFullYear();
  readonly currentTime = signal<string>(new Date().toLocaleTimeString('es-ES'));
  private clockInterval?: any;

  ngOnInit() {
    this.community.initSocket();
    this.clockInterval = setInterval(() => {
      this.currentTime.set(new Date().toLocaleTimeString('es-ES'));
    }, 1000);
  }

  ngOnDestroy() {
    this.community.disconnectSocket();
    if (this.clockInterval) {
      clearInterval(this.clockInterval);
    }
  }

  userInitial(): string {
    const name = this.auth.currentUser()?.displayName || this.auth.currentUser()?.username || 'A';
    return name.charAt(0).toUpperCase();
  }
}
