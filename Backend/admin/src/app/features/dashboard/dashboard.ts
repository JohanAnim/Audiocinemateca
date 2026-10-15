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
      <div class="dash-header">
        <div>
          <h1>Métricas & Rendimiento Ejecutivo</h1>
          <p class="subtitle">Supervisión en tiempo real del ecosistema Audiocinemateca</p>
        </div>
        <div class="header-actions">
          <button class="btn btn-secondary" (click)="loadStats()" [disabled]="isLoading()">
            <span>🔄</span>
            <span>Actualizar</span>
          </button>
          <a routerLink="/catalog/create" class="btn btn-primary">
            <span>✨</span>
            <span>Nueva Ficha Exclusiva</span>
          </a>
        </div>
      </div>

      @if (isLoading() && !stats()) {
        <div class="loading-state">
          <div class="spinner"></div>
          <p>Cargando métricas ejecutivas de la base de datos...</p>
        </div>
      }

      @if (stats(); as s) {
        <!-- TARJETAS PRINCIPALES DE KPI -->
        <div class="kpi-grid">
          <!-- CARD 1: USUARIOS -->
          <div class="card kpi-card">
            <div class="kpi-icon-wrap user-gradient">👥</div>
            <div class="kpi-data">
              <span class="kpi-label">Usuarios Registrados</span>
              <div class="kpi-value">{{ s.users.total }}</div>
              <div class="kpi-sub">
                <span class="badge badge-success">🟢 {{ community.onlineUsers().length || s.users.onlineNow }} en vivo</span>
              </div>
            </div>
          </div>

          <!-- CARD 2: CATÁLOGO -->
          <div class="card kpi-card">
            <div class="kpi-icon-wrap catalog-gradient">🎬</div>
            <div class="kpi-data">
              <span class="kpi-label">Obras en Catálogo</span>
              <div class="kpi-value">{{ s.catalog.total }}</div>
              <div class="kpi-sub">
                <span class="badge badge-exclusive">⭐ {{ s.catalog.exclusive }} Exclusivas</span>
                <span class="sub-text">{{ s.catalog.episodes }} episodios</span>
              </div>
            </div>
          </div>

          <!-- CARD 3: ENGAGEMENT / VISTAS -->
          <div class="card kpi-card">
            <div class="kpi-icon-wrap views-gradient">🎧</div>
            <div class="kpi-data">
              <span class="kpi-label">Reproducciones Totales</span>
              <div class="kpi-value">{{ s.engagement.totalViews }}</div>
              <div class="kpi-sub">
                <span class="sub-text">💬 {{ s.engagement.totalComments }} comentarios</span>
              </div>
            </div>
          </div>

          <!-- CARD 4: CALIFICACIONES -->
          <div class="card kpi-card">
            <div class="kpi-icon-wrap rating-gradient">⭐</div>
            <div class="kpi-data">
              <span class="kpi-label">Valoración Global</span>
              <div class="kpi-value">{{ s.engagement.averageRating }} <span class="max-star">/ 5.0</span></div>
              <div class="kpi-sub">
                <span class="sub-text">{{ s.engagement.totalRatings }} reseñas auditadas</span>
              </div>
            </div>
          </div>
        </div>

        <!-- FILA DE PANELES MEDIOS: PLATAFORMAS & SALUD DEL SISTEMA -->
        <div class="grid-2-col">
          <!-- PANEL DE DISPOSITIVOS Y PLATAFORMAS -->
          <div class="card">
            <div class="card-header-clean">
              <h3>📱 Distribución de Plataformas & Dispositivos</h3>
              <span class="badge badge-info">Apps Nativas & Web</span>
            </div>
            <p class="section-desc">Dispositivos registrados mediante telemetría accesible:</p>

            <div class="platforms-list">
              <div class="platform-row">
                <div class="platform-meta">
                  <span class="platform-icon">🤖</span>
                  <span class="platform-name">Android (Teléfonos, Tablets & TV)</span>
                </div>
                <div class="platform-count badge badge-primary">{{ s.platforms.Android }}</div>
              </div>

              <div class="platform-row">
                <div class="platform-meta">
                  <span class="platform-icon">🪟</span>
                  <span class="platform-name">Windows (Audiocinemateca Desktop)</span>
                </div>
                <div class="platform-count badge badge-secondary">{{ s.platforms.Windows }}</div>
              </div>

              <div class="platform-row">
                <div class="platform-meta">
                  <span class="platform-icon">🍏</span>
                  <span class="platform-name">macOS</span>
                </div>
                <div class="platform-count badge badge-secondary">{{ s.platforms.macOS }}</div>
              </div>

              <div class="platform-row">
                <div class="platform-meta">
                  <span class="platform-icon">🐧</span>
                  <span class="platform-name">Linux (App Desktop & CLI)</span>
                </div>
                <div class="platform-count badge badge-secondary">{{ s.platforms.Linux }}</div>
              </div>

              <div class="platform-row">
                <div class="platform-meta">
                  <span class="platform-icon">🌐</span>
                  <span class="platform-name">Navegador Web & Smart TV</span>
                </div>
                <div class="platform-count badge badge-secondary">{{ s.platforms.Web }}</div>
              </div>
            </div>
          </div>

          <!-- PANEL DE SALUD Y SERVIDOR -->
          <div class="card">
            <div class="card-header-clean">
              <h3>⚡ Infraestructura & Servidor Propio</h3>
              <span class="badge badge-success">PostgreSQL 16 OK</span>
            </div>
            <p class="section-desc">Estado de ejecución del backend independiente:</p>

            <div class="sys-metrics">
              <div class="sys-metric-item">
                <span class="metric-title">Tiempo de Actividad (Uptime)</span>
                <span class="metric-val">{{ formatUptime(s.system.uptimeSeconds) }}</span>
              </div>

              <div class="sys-metric-item">
                <span class="metric-title">Memoria RAM Utilizada (RSS)</span>
                <span class="metric-val">{{ s.system.memoryRssMb }} MB</span>
              </div>

              <div class="sys-metric-item">
                <span class="metric-title">Heap de Node.js</span>
                <span class="metric-val">{{ s.system.memoryHeapUsedMb }} MB</span>
              </div>

              <div class="sys-metric-item">
                <span class="metric-title">Versión Runtime</span>
                <span class="metric-val">{{ s.system.nodeVersion }} (NestJS 12 ESM)</span>
              </div>

              <div class="sys-metric-item">
                <span class="metric-title">Hora del Servidor</span>
                <span class="metric-val">{{ s.system.serverTime | date:'medium' }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- ACCESOS DIRECTOS -->
        <div class="card quick-actions-card">
          <h3>🚀 Acciones Rápidas de Administración</h3>
          <div class="actions-grid">
            <a routerLink="/catalog/create" class="action-btn">
              <span class="action-icon">➕</span>
              <div>
                <strong>Crear Ficha de Catálogo</strong>
                <small>Película, Serie, Corto o Documental Exclusivo</small>
              </div>
            </a>

            <a routerLink="/community" class="action-btn">
              <span class="action-icon">📢</span>
              <div>
                <strong>Publicar Comunicado Oficial</strong>
                <small>Aviso para toda la comunidad en la app</small>
              </div>
            </a>

            <a routerLink="/users" class="action-btn">
              <span class="action-icon">🛡️</span>
              <div>
                <strong>Gestionar y Moderar Usuarios</strong>
                <small>Cambiar roles, ver dispositivos y sanciones</small>
              </div>
            </a>

            <a routerLink="/config" class="action-btn">
              <span class="action-icon">📲</span>
              <div>
                <strong>Configurar Versión del APK</strong>
                <small>Control del actualizador automático</small>
              </div>
            </a>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .dashboard-page {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .dash-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 16px;
    }

    h1 {
      font-size: 24px;
      font-weight: 800;
      color: var(--text-main);
    }

    .subtitle {
      font-size: 14px;
      color: var(--text-muted);
      margin-top: 2px;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 12px;
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
      width: 56px;
      height: 56px;
      border-radius: var(--radius-md);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 26px;
      flex-shrink: 0;
    }

    .user-gradient { background: linear-gradient(135deg, rgba(99, 102, 241, 0.25), rgba(139, 92, 246, 0.1)); }
    .catalog-gradient { background: linear-gradient(135deg, rgba(236, 72, 153, 0.25), rgba(244, 63, 94, 0.1)); }
    .views-gradient { background: linear-gradient(135deg, rgba(16, 185, 129, 0.25), rgba(5, 150, 105, 0.1)); }
    .rating-gradient { background: linear-gradient(135deg, rgba(245, 158, 11, 0.25), rgba(217, 119, 6, 0.1)); }

    .kpi-data {
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    .kpi-label {
      font-size: 12px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: var(--text-muted);
    }

    .kpi-value {
      font-size: 28px;
      font-weight: 800;
      color: var(--text-main);
      line-height: 1.2;
      margin: 4px 0;
    }

    .max-star {
      font-size: 16px;
      color: var(--text-dim);
    }

    .kpi-sub {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .sub-text {
      font-size: 12px;
      color: var(--text-dim);
    }

    /* --- FILAS DE 2 COLUMNAS --- */
    .grid-2-col {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
      gap: 20px;
    }

    .card-header-clean {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 4px;
    }

    .card-header-clean h3 {
      font-size: 16px;
      font-weight: 700;
      color: var(--text-main);
    }

    .section-desc {
      font-size: 13px;
      color: var(--text-muted);
      margin-bottom: 16px;
    }

    .platforms-list {
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
      font-size: 14px;
      font-weight: 600;
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
      padding-bottom: 10px;
      border-bottom: 1px solid var(--border-color);
      font-size: 13px;
    }

    .sys-metric-item:last-child {
      border-bottom: none;
      padding-bottom: 0;
    }

    .metric-title {
      color: var(--text-muted);
    }

    .metric-val {
      font-weight: 700;
      font-family: monospace;
      color: var(--text-main);
    }

    /* --- ACCIONES RÁPIDAS --- */
    .quick-actions-card h3 {
      font-size: 16px;
      font-weight: 700;
      margin-bottom: 16px;
    }

    .actions-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
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
      transition: all 0.2s;
    }

    .action-btn:hover {
      background-color: var(--bg-card-hover);
      border-color: var(--primary);
      transform: translateY(-2px);
    }

    .action-icon {
      font-size: 24px;
    }

    .action-btn strong {
      display: block;
      font-size: 14px;
      color: var(--text-main);
    }

    .action-btn small {
      font-size: 12px;
      color: var(--text-dim);
    }

    .loading-state {
      padding: 60px;
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 16px;
      color: var(--text-muted);
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
    return `${m}m ${seconds % 60}s`;
  }
}
