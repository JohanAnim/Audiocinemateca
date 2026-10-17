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
      <header class="dash-header" aria-labelledby="dash-title">
        <div>
          <h1 id="dash-title">Resumen General</h1>
        </div>
        <div class="header-actions">
          <button
            class="btn btn-secondary cursor-pointer"
            (click)="loadStats()"
            [disabled]="isLoading()"
          >
            {{ isLoading() ? 'Actualizando...' : 'Actualizar' }}
          </button>
          <a routerLink="/catalog/create" class="btn btn-primary cursor-pointer">
            Nueva Ficha
          </a>
        </div>
      </header>

      @if (isLoading() && !stats()) {
        <div class="loading-state card" role="status" aria-live="polite">
          <div class="spinner" aria-hidden="true"></div>
          <p>Cargando estadísticas...</p>
        </div>
      }

      @if (stats(); as s) {
        <section aria-labelledby="kpi-section-title">
          <h2 id="kpi-section-title" class="sr-only">Métricas principales</h2>
          <div class="kpi-grid">
            <article class="card kpi-card" aria-labelledby="kpi-users-label">
              <div class="kpi-data">
                <h3 id="kpi-users-label" class="kpi-label">Usuarios</h3>
                <div class="kpi-value">{{ s.users.total }}</div>
                <div class="kpi-sub">
                  <span class="badge badge-success">
                    {{ community.onlineUsers().length || s.users.onlineNow }} en línea
                  </span>
                </div>
              </div>
            </article>

            <article class="card kpi-card" aria-labelledby="kpi-catalog-label">
              <div class="kpi-data">
                <h3 id="kpi-catalog-label" class="kpi-label">Catálogo</h3>
                <div class="kpi-value">{{ s.catalog.total }}</div>
                <div class="kpi-sub">
                  <span class="sub-text">{{ s.catalog.exclusive }} exclusivas • {{ s.catalog.episodes }} episodios</span>
                </div>
              </div>
            </article>

            <article class="card kpi-card" aria-labelledby="kpi-views-label">
              <div class="kpi-data">
                <h3 id="kpi-views-label" class="kpi-label">Reproducciones</h3>
                <div class="kpi-value">{{ s.engagement.totalViews }}</div>
                <div class="kpi-sub">
                  <span class="sub-text">{{ s.engagement.totalComments }} comentarios</span>
                </div>
              </div>
            </article>

            <article class="card kpi-card" aria-labelledby="kpi-rating-label">
              <div class="kpi-data">
                <h3 id="kpi-rating-label" class="kpi-label">Valoración Media</h3>
                <div class="kpi-value">{{ s.engagement.averageRating }} <span class="max-star">/ 5.0</span></div>
                <div class="kpi-sub">
                  <span class="sub-text">{{ s.engagement.totalRatings }} calificaciones</span>
                </div>
              </div>
            </article>
          </div>
        </section>

        <div class="grid-2-col">
          <section class="card" aria-labelledby="platforms-title">
            <div class="card-header-clean">
              <h2 id="platforms-title">Dispositivos por Plataforma</h2>
            </div>

            <ul class="platforms-list" role="list">
              <li class="platform-row">
                <span class="platform-name">Android</span>
                <span class="platform-count badge badge-primary">{{ s.platforms.Android }}</span>
              </li>
              <li class="platform-row">
                <span class="platform-name">Windows</span>
                <span class="platform-count badge badge-secondary">{{ s.platforms.Windows }}</span>
              </li>
              <li class="platform-row">
                <span class="platform-name">macOS</span>
                <span class="platform-count badge badge-secondary">{{ s.platforms.macOS }}</span>
              </li>
              <li class="platform-row">
                <span class="platform-name">Linux</span>
                <span class="platform-count badge badge-secondary">{{ s.platforms.Linux }}</span>
              </li>
              <li class="platform-row">
                <span class="platform-name">Web</span>
                <span class="platform-count badge badge-secondary">{{ s.platforms.Web }}</span>
              </li>
            </ul>
          </section>

          <section class="card" aria-labelledby="server-health-title">
            <div class="card-header-clean">
              <h2 id="server-health-title">Estado del Servidor</h2>
              <span class="badge badge-success">PostgreSQL Activo</span>
            </div>

            <dl class="sys-metrics">
              <div class="sys-metric-item">
                <dt class="metric-title">Tiempo activo</dt>
                <dd class="metric-val font-mono">{{ formatUptime(s.system.uptimeSeconds) }}</dd>
              </div>
              <div class="sys-metric-item">
                <dt class="metric-title">Memoria RAM (RSS)</dt>
                <dd class="metric-val font-mono">{{ s.system.memoryRssMb }} MB</dd>
              </div>
              <div class="sys-metric-item">
                <dt class="metric-title">Memoria Heap</dt>
                <dd class="metric-val font-mono">{{ s.system.memoryHeapUsedMb }} MB</dd>
              </div>
              <div class="sys-metric-item">
                <dt class="metric-title">Node.js</dt>
                <dd class="metric-val font-mono">{{ s.system.nodeVersion }}</dd>
              </div>
              <div class="sys-metric-item">
                <dt class="metric-title">Hora del servidor</dt>
                <dd class="metric-val font-mono">{{ s.system.serverTime | date:'short' }}</dd>
              </div>
            </dl>
          </section>
        </div>

        <section class="card quick-actions-card" aria-labelledby="quick-actions-title">
          <h2 id="quick-actions-title">Accesos Rápidos</h2>
          <div class="actions-grid">
            <a routerLink="/catalog" class="action-btn cursor-pointer">
              <div>
                <strong>Catálogo y Sincronización</strong>
                <small>Gestión de títulos y sincronización con el servidor maestro</small>
              </div>
            </a>

            <a routerLink="/catalog/create" class="action-btn cursor-pointer">
              <div>
                <strong>Nueva Ficha</strong>
                <small>Registrar película, serie, documental o corto</small>
              </div>
            </a>

            <a routerLink="/config" class="action-btn cursor-pointer">
              <div>
                <strong>Versiones APK y Banner</strong>
                <small>GitHub Releases, descargas y configuración general</small>
              </div>
            </a>

            <a routerLink="/agents" class="action-btn cursor-pointer">
              <div>
                <strong>Aura IA y Notificaciones Push</strong>
                <small>Proveedor de IA, modelo, colecciones y envíos Push</small>
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
