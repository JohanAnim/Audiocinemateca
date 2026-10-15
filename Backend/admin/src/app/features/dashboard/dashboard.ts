import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { StatsService } from '../../core/services/stats.service';
import { CommunityService } from '../../core/services/community.service';
import { ExecutiveStats } from '../../core/models/stats.models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="dashboard-page">
      <!-- HEADER DEL DASHBOARD -->
      <header class="dash-header" aria-labelledby="dash-title">
        <div>
          <h1 id="dash-title">Panel Ejecutivo & Métricas en Tiempo Real</h1>
          <p class="subtitle">Monitoreo continuo de audiencia, telemetría de infraestructura y distribución de catálogo</p>
        </div>
        <div class="header-actions">
          <button
            class="btn btn-secondary cursor-pointer"
            (click)="loadStats()"
            [disabled]="isLoading()"
            aria-label="Actualizar todas las métricas ejecutivas de la base de datos"
          >
            <span aria-hidden="true">🔄</span>
            <span>Actualizar Métricas</span>
          </button>
          <a
            routerLink="/catalog/create"
            class="btn btn-primary cursor-pointer"
            aria-label="Crear una nueva ficha de obra para el catálogo exclusivo"
          >
            <span aria-hidden="true">✨</span>
            <span>Nueva Ficha Exclusiva</span>
          </a>
        </div>
      </header>

      @if (isLoading() && !stats()) {
        <div class="loading-state card" role="status" aria-live="polite">
          <div class="spinner" aria-hidden="true"></div>
          <p>Consultando métricas en la base de datos PostgreSQL...</p>
        </div>
      }

      @if (stats(); as s) {
        <!-- SECCIÓN 1: INDICADORES CLAVE DE RENDIMIENTO (KPIs) -->
        <section aria-labelledby="kpi-section-title">
          <h2 id="kpi-section-title" class="sr-only">Indicadores Clave de Rendimiento (KPIs)</h2>
          <div class="kpi-grid">
            <!-- CARD 1: USUARIOS -->
            <article class="card kpi-card" aria-labelledby="kpi-users-label">
              <div class="kpi-icon-wrap user-gradient" aria-hidden="true">👥</div>
              <div class="kpi-data">
                <h3 id="kpi-users-label" class="kpi-label">Usuarios Registrados</h3>
                <div class="kpi-value" aria-label="{{ s.users.total }} usuarios totales">{{ s.users.total }}</div>
                <div class="kpi-sub">
                  <span class="badge badge-success" role="status" aria-live="polite">
                    🟢 {{ community.onlineUsers().length || s.users.onlineNow }} activos ahora
                  </span>
                </div>
              </div>
            </article>

            <!-- CARD 2: CATÁLOGO -->
            <article class="card kpi-card" aria-labelledby="kpi-catalog-label">
              <div class="kpi-icon-wrap catalog-gradient" aria-hidden="true">🎬</div>
              <div class="kpi-data">
                <h3 id="kpi-catalog-label" class="kpi-label">Obras en Catálogo</h3>
                <div class="kpi-value">{{ s.catalog.total }}</div>
                <div class="kpi-sub">
                  <span class="badge badge-exclusive">⭐ {{ s.catalog.exclusive }} Exclusivas</span>
                  <span class="sub-text">{{ s.catalog.episodes }} episodios indexados</span>
                </div>
              </div>
            </article>

            <!-- CARD 3: AUDIENCIA Y REPRODUCCIONES -->
            <article class="card kpi-card" aria-labelledby="kpi-views-label">
              <div class="kpi-icon-wrap views-gradient" aria-hidden="true">🎧</div>
              <div class="kpi-data">
                <h3 id="kpi-views-label" class="kpi-label">Reproducciones Acumuladas</h3>
                <div class="kpi-value">{{ s.engagement.totalViews }}</div>
                <div class="kpi-sub">
                  <span class="sub-text">💬 {{ s.engagement.totalComments }} comentarios activos</span>
                </div>
              </div>
            </article>

            <!-- CARD 4: CALIFICACIONES AUDITADAS -->
            <article class="card kpi-card" aria-labelledby="kpi-rating-label">
              <div class="kpi-icon-wrap rating-gradient" aria-hidden="true">⭐</div>
              <div class="kpi-data">
                <h3 id="kpi-rating-label" class="kpi-label">Valoración Promedio</h3>
                <div class="kpi-value">{{ s.engagement.averageRating }} <span class="max-star">/ 5.0</span></div>
                <div class="kpi-sub">
                  <span class="sub-text">{{ s.engagement.totalRatings }} valoraciones auditadas</span>
                </div>
              </div>
            </article>
          </div>
        </section>

        <!-- SECCIÓN 2: PLATAFORMAS Y SALUD DE INFRAESTRUCTURA -->
        <div class="grid-2-col">
          <!-- PANEL DE DISPOSITIVOS Y PLATAFORMAS -->
          <section class="card" aria-labelledby="platforms-title">
            <div class="card-header-clean">
              <h2 id="platforms-title">Distribución por Dispositivos y Clientes</h2>
              <span class="badge badge-info">Apps Nativas & Web</span>
            </div>
            <p class="section-desc">Ecosistema de clientes reportados mediante telemetría segura:</p>

            <ul class="platforms-list" role="list">
              <li class="platform-row">
                <div class="platform-meta">
                  <span class="platform-icon" aria-hidden="true">🤖</span>
                  <span class="platform-name">Android (Smartphones, Tablets & Android TV)</span>
                </div>
                <span class="platform-count badge badge-primary" aria-label="{{ s.platforms.Android }} dispositivos Android">{{ s.platforms.Android }}</span>
              </li>

              <li class="platform-row">
                <div class="platform-meta">
                  <span class="platform-icon" aria-hidden="true">🪟</span>
                  <span class="platform-name">Windows (Audiocinemateca Desktop)</span>
                </div>
                <span class="platform-count badge badge-secondary" aria-label="{{ s.platforms.Windows }} dispositivos Windows">{{ s.platforms.Windows }}</span>
              </li>

              <li class="platform-row">
                <div class="platform-meta">
                  <span class="platform-icon" aria-hidden="true">🍏</span>
                  <span class="platform-name">Apple macOS (Desktop)</span>
                </div>
                <span class="platform-count badge badge-secondary" aria-label="{{ s.platforms.macOS }} dispositivos macOS">{{ s.platforms.macOS }}</span>
              </li>

              <li class="platform-row">
                <div class="platform-meta">
                  <span class="platform-icon" aria-hidden="true">🐧</span>
                  <span class="platform-name">GNU/Linux (Aplicación Desktop & Terminal)</span>
                </div>
                <span class="platform-count badge badge-secondary" aria-label="{{ s.platforms.Linux }} dispositivos Linux">{{ s.platforms.Linux }}</span>
              </li>

              <li class="platform-row">
                <div class="platform-meta">
                  <span class="platform-icon" aria-hidden="true">🌐</span>
                  <span class="platform-name">Navegadores Web & Smart TV Hubs</span>
                </div>
                <span class="platform-count badge badge-secondary" aria-label="{{ s.platforms.Web }} sesiones Web">{{ s.platforms.Web }}</span>
              </li>
            </ul>
          </section>

          <!-- PANEL DE SALUD Y SERVIDOR -->
          <section class="card" aria-labelledby="server-health-title">
            <div class="card-header-clean">
              <h2 id="server-health-title">Salud de la Infraestructura Soberana</h2>
              <span class="badge badge-success">PostgreSQL 16 Activo</span>
            </div>
            <p class="section-desc">Métricas de rendimiento en tiempo real del nodo NestJS:</p>

            <dl class="sys-metrics">
              <div class="sys-metric-item">
                <dt class="metric-title">Tiempo Activo (Uptime Continuo)</dt>
                <dd class="metric-val font-mono">{{ formatUptime(s.system.uptimeSeconds) }}</dd>
              </div>

              <div class="sys-metric-item">
                <dt class="metric-title">Memoria RAM Asignada (RSS)</dt>
                <dd class="metric-val font-mono">{{ s.system.memoryRssMb }} MB</dd>
              </div>

              <div class="sys-metric-item">
                <dt class="metric-title">Memoria Heap Utilizada</dt>
                <dd class="metric-val font-mono">{{ s.system.memoryHeapUsedMb }} MB</dd>
              </div>

              <div class="sys-metric-item">
                <dt class="metric-title">Entorno de Ejecución</dt>
                <dd class="metric-val font-mono">{{ s.system.nodeVersion }} (NestJS 12 ESM)</dd>
              </div>

              <div class="sys-metric-item">
                <dt class="metric-title">Hora Sincronizada de Base de Datos</dt>
                <dd class="metric-val font-mono">{{ s.system.serverTime | date:'medium' }}</dd>
              </div>
            </dl>
          </section>
        </div>

        <!-- SECCIÓN 3: ACCIONES RÁPIDAS DE GESTIÓN -->
        <section class="card quick-actions-card" aria-labelledby="quick-actions-title">
          <h2 id="quick-actions-title">Acciones Directas de Administración</h2>
          <div class="actions-grid">
            <a routerLink="/catalog/create" class="action-btn cursor-pointer">
              <span class="action-icon" aria-hidden="true">➕</span>
              <div>
                <strong>Crear Ficha Cinematográfica</strong>
                <small>Alta de Película, Serie, Corto o Documental con selector multilingüe</small>
              </div>
            </a>

            <a routerLink="/community" class="action-btn cursor-pointer">
              <span class="action-icon" aria-hidden="true">📢</span>
              <div>
                <strong>Publicar Comunicado Oficial</strong>
                <small>Aviso prioritario en tiempo real para todos los clientes de la app</small>
              </div>
            </a>

            <a routerLink="/users" class="action-btn cursor-pointer">
              <span class="action-icon" aria-hidden="true">🛡️</span>
              <div>
                <strong>Gestionar y Moderar Usuarios</strong>
                <small>Asignar roles, inspeccionar UIDs y moderar sanciones en caliente</small>
              </div>
            </a>

            <a routerLink="/config" class="action-btn cursor-pointer">
              <span class="action-icon" aria-hidden="true">📲</span>
              <div>
                <strong>Configurar Versión del APK</strong>
                <small>Control del actualizador automático, changelogs y descargas directas</small>
              </div>
            </a>
          </div>
        </section>
      }
    </div>
  `,
  styles: [`
    .dashboard-page {
      display: flex;
      flex-direction: column;
      gap: 28px;
    }

    .dash-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 16px;
    }

    h1 {
      font-size: 26px;
      font-weight: 800;
      color: var(--text-main);
      letter-spacing: -0.5px;
    }

    .subtitle {
      font-size: 14px;
      color: var(--text-muted);
      margin-top: 4px;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .loading-state {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 14px;
      padding: 40px;
      color: var(--text-muted);
    }

    .spinner {
      width: 20px;
      height: 20px;
      border: 2px solid rgba(255, 255, 255, 0.2);
      border-top-color: var(--primary);
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    /* --- KPIS GRID --- */
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 20px;
    }

    .kpi-card {
      display: flex;
      align-items: center;
      gap: 18px;
      padding: 22px;
    }

    .kpi-icon-wrap {
      width: 54px;
      height: 54px;
      border-radius: var(--radius-md);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      flex-shrink: 0;
    }

    .user-gradient { background: rgba(99, 102, 241, 0.15); border: 1px solid rgba(99, 102, 241, 0.3); }
    .catalog-gradient { background: rgba(236, 72, 153, 0.15); border: 1px solid rgba(236, 72, 153, 0.3); }
    .views-gradient { background: rgba(16, 185, 129, 0.15); border: 1px solid rgba(16, 185, 129, 0.3); }
    .rating-gradient { background: rgba(245, 158, 11, 0.15); border: 1px solid rgba(245, 158, 11, 0.3); }

    .kpi-data {
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    .kpi-label {
      font-size: 13px;
      font-weight: 600;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .kpi-value {
      font-size: 30px;
      font-weight: 800;
      color: var(--text-main);
      line-height: 1.1;
      margin: 4px 0 6px 0;
    }

    .max-star {
      font-size: 16px;
      font-weight: 500;
      color: var(--text-dim);
    }

    .kpi-sub {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 12px;
    }

    .sub-text {
      color: var(--text-dim);
    }

    /* --- FILAS MEDIAS --- */
    .grid-2-col {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
      gap: 24px;
    }

    .card-header-clean {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 6px;
    }

    .card-header-clean h2 {
      font-size: 16px;
      font-weight: 700;
      color: var(--text-main);
    }

    .section-desc {
      font-size: 13px;
      color: var(--text-dim);
      margin-bottom: 18px;
    }

    .platforms-list {
      list-style: none;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .platform-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px 14px;
      background-color: var(--bg-input);
      border-radius: var(--radius-sm);
      border: 1px solid var(--border-color);
    }

    .platform-meta {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 13px;
      font-weight: 600;
    }

    .platform-icon {
      font-size: 18px;
    }

    .sys-metrics {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .sys-metric-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px 14px;
      background-color: var(--bg-input);
      border-radius: var(--radius-sm);
      border: 1px solid var(--border-color);
      font-size: 13px;
    }

    .metric-title {
      color: var(--text-muted);
      font-weight: 500;
    }

    .metric-val {
      font-weight: 700;
      color: var(--text-main);
    }

    /* --- ACCIONES RÁPIDAS --- */
    .quick-actions-card h2 {
      font-size: 16px;
      font-weight: 700;
      color: var(--text-main);
      margin-bottom: 16px;
    }

    .actions-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 14px;
    }

    .action-btn {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 16px;
      background-color: var(--bg-input);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-sm);
      transition: all 0.2s ease;
      color: var(--text-main);
    }

    .action-btn:hover {
      border-color: var(--primary);
      background-color: rgba(99, 102, 241, 0.08);
      transform: translateY(-2px);
    }

    .action-icon {
      font-size: 24px;
      flex-shrink: 0;
    }

    .action-btn strong {
      display: block;
      font-size: 14px;
      font-weight: 700;
      color: var(--text-main);
    }

    .action-btn small {
      display: block;
      font-size: 12px;
      color: var(--text-dim);
      margin-top: 2px;
      line-height: 1.3;
    }
  `],
})
export class DashboardComponent implements OnInit {
  private readonly statsService = inject(StatsService);
  readonly community = inject(CommunityService);

  readonly stats = signal<ExecutiveStats | null>(null);
  readonly isLoading = signal<boolean>(false);

  ngOnInit() {
    this.loadStats();
  }

  loadStats() {
    this.isLoading.set(true);
    this.statsService.getExecutiveStats().subscribe({
      next: (data) => {
        this.stats.set(data);
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
      },
    });
  }

  formatUptime(seconds: number): string {
    const d = Math.floor(seconds / (3600 * 24));
    const h = Math.floor((seconds % (3600 * 24)) / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    if (d > 0) return `${d}d ${h}h ${m}m`;
    if (h > 0) return `${h}h ${m}m`;
    return `${Math.floor(seconds)}s`;
  }
}
