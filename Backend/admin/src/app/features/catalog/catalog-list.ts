import { Component, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ContentService } from '../../core/services/content.service';
import { ContentItem } from '../../core/models/content.models';

@Component({
  selector: 'app-catalog-list',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  template: `
    <div class="exec-page">
      <div class="exec-header">
        <div class="exec-title-group">
          <h1>Catálogo</h1>
        </div>
        <div class="exec-header-actions">
          <button
            type="button"
            class="btn btn-secondary"
            (click)="showSyncPanel.set(!showSyncPanel())"
            [attr.aria-expanded]="showSyncPanel()"
          >
            {{ showSyncPanel() ? 'Ocultar Sincronización' : 'Sincronización del Catálogo' }}
          </button>
          <a routerLink="/catalog/create" class="btn btn-primary">
            Nueva Ficha
          </a>
        </div>
      </div>

      @if (statusMessage()) {
        <div class="status-banner" [ngClass]="statusType()" role="status" aria-live="polite">
          <span>{{ statusMessage() }}</span>
          <button type="button" class="btn btn-secondary btn-sm" (click)="statusMessage.set(null)" aria-label="Cerrar aviso">✕</button>
        </div>
      }

      @if (showSyncPanel()) {
        <section class="card sync-card" aria-labelledby="sync-section-title">
          <div class="section-header-row">
            <div>
              <h2 id="sync-section-title">Actualización del Catálogo</h2>
            </div>
            <div class="sync-badges">
              @if (syncStatus()?.syncProgress?.isRunning) {
                <span class="badge badge-info">Sincronizando...</span>
              } @else if (syncStatus()?.isUpToDate) {
                <span class="badge badge-success">Al día (v{{ syncStatus()?.localVersion?.version || 'Actual' }})</span>
              } @else {
                <span class="badge badge-warning">Revisión pendiente</span>
              }
            </div>
          </div>

          <div class="grid-4">
            <div class="kpi-mini-card">
              <span class="kpi-label">Versión Local</span>
              <strong class="kpi-val">{{ syncStatus()?.localVersion?.version || '—' }}</strong>
              <span class="kpi-sub">{{ syncStatus()?.localVersion?.total_items || syncStatus()?.fileStats?.total || 0 }} títulos</span>
            </div>

            <div class="kpi-mini-card">
              <span class="kpi-label">Archivo Maestro</span>
              <strong class="kpi-val">{{ syncStatus()?.fileStats?.total || 0 | number }}</strong>
              <span class="kpi-sub">
                {{ syncStatus()?.fileStats?.movies || 0 }} películas • {{ syncStatus()?.fileStats?.series || 0 }} series
              </span>
            </div>

            <div class="kpi-mini-card">
              <span class="kpi-label">Base de Datos</span>
              <strong class="kpi-val">{{ syncStatus()?.databaseStats?.totalContents || totalItems() | number }}</strong>
              <span class="kpi-sub">
                {{ syncStatus()?.databaseStats?.totalSeasons || 0 }} temporadas • {{ syncStatus()?.databaseStats?.totalEpisodes || 0 }} episodios
              </span>
            </div>

            <div class="kpi-mini-card">
              <span class="kpi-label">Última Comprobación</span>
              <strong class="kpi-val" style="font-size: 16px;">
                {{ syncStatus()?.lastCheckAt ? (syncStatus()?.lastCheckAt | date:'short') : 'Reciente' }}
              </strong>
              <span class="kpi-sub">
                Remota: {{ syncStatus()?.remoteVersion?.version || syncStatus()?.localVersion?.version || '—' }}
              </span>
            </div>
          </div>

          @if (syncStatus()?.syncProgress?.isRunning || isSyncing()) {
            <div class="sync-progress-box" role="status" aria-live="polite">
              <div class="sync-progress-header">
                <strong>{{ syncStatus()?.syncProgress?.phase || 'Sincronizando...' }}</strong>
                <span>
                  {{ syncStatus()?.syncProgress?.processed || 0 | number }} / {{ syncStatus()?.syncProgress?.total || syncStatus()?.fileStats?.total || 11432 | number }}
                  ({{ getSyncPercentage() }}%)
                </span>
              </div>
              <div class="progress-track">
                <div class="progress-fill" [style.width.%]="getSyncPercentage()"></div>
              </div>
            </div>
          }

          <div class="card-footer-actions">
            <span></span>
            <div class="sync-buttons">
              <button
                type="button"
                class="btn btn-secondary"
                (click)="checkRemoteCatalog(false)"
                [disabled]="isCheckingRemote() || isSyncing()"
              >
                {{ isCheckingRemote() ? 'Comprobando...' : 'Comprobar Versión Remota' }}
              </button>

              <button
                type="button"
                class="btn btn-primary"
                (click)="syncCatalogNow(false)"
                [disabled]="isSyncing() || syncStatus()?.syncProgress?.isRunning"
              >
                {{ isSyncing() ? 'Sincronizando...' : 'Sincronizar Base de Datos' }}
              </button>

              <button
                type="button"
                class="btn btn-success"
                (click)="checkRemoteCatalog(true)"
                [disabled]="isCheckingRemote() || isSyncing()"
              >
                Actualizar y Notificar Push
              </button>
            </div>
          </div>
        </section>
      }

      <section class="card filters-card" aria-label="Búsqueda y filtros del catálogo">
        <div class="filters-row">
          <div class="search-box">
            <label for="catalog-search" class="sr-only">Buscar en el catálogo</label>
            <input
              id="catalog-search"
              type="text"
              class="form-control"
              placeholder="Buscar por título, director, narrador o ID..."
              [(ngModel)]="searchQuery"
              (keyup.enter)="applyFilters()"
            />
          </div>

          <div>
            <label for="catalog-type" class="sr-only">Tipo de contenido</label>
            <select id="catalog-type" class="form-control filter-select" [(ngModel)]="selectedType" (change)="applyFilters()">
              <option value="">Todos los formatos</option>
              <option value="MOVIE">Películas</option>
              <option value="SERIES">Series</option>
              <option value="DOCUMENTARY">Documentales</option>
              <option value="SHORT">Cortometrajes</option>
            </select>
          </div>

          <div>
            <label for="catalog-status" class="sr-only">Estado</label>
            <select id="catalog-status" class="form-control filter-select" [(ngModel)]="selectedStatus" (change)="applyFilters()">
              <option value="ALL">Todos los estados</option>
              <option value="PUBLISHED">Publicados</option>
              <option value="DRAFT">Borradores</option>
            </select>
          </div>

          <div>
            <label for="catalog-exclusive" class="sr-only">Origen</label>
            <select id="catalog-exclusive" class="form-control filter-select" [(ngModel)]="selectedExclusive" (change)="applyFilters()">
              <option value="">Todo el catálogo</option>
              <option value="true">Solo Exclusivas</option>
            </select>
          </div>

          <button
            type="button"
            class="btn btn-primary"
            (click)="applyFilters()"
          >
            Filtrar
          </button>
        </div>
      </section>

      @if (isLoading()) {
        <div class="card loading-card" role="status" aria-live="polite">
          <p>Cargando catálogo...</p>
        </div>
      } @else if (contents().length === 0) {
        <div class="card empty-card" role="status">
          <h2>Sin resultados</h2>
          <div style="display: flex; gap: 12px; flex-wrap: wrap; justify-content: center; margin-top: 8px;">
            <button type="button" class="btn btn-secondary" (click)="syncCatalogNow(false)">Sincronizar Catálogo</button>
            <a routerLink="/catalog/create" class="btn btn-primary">Nueva Ficha</a>
          </div>
        </div>
      } @else {
        <div class="card table-card">
          <div class="table-responsive">
            <table class="table" role="table">
              <caption class="sr-only">Listado de obras cinematográficas indexadas con audiodescripción</caption>
              <thead>
                <tr>
                  <th scope="col">Obra & Ficha</th>
                  <th scope="col">Formato</th>
                  <th scope="col">Idioma / Doblaje</th>
                  <th scope="col">Audiodescripción</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Vistas / Rating</th>
                  <th scope="col">Acciones</th>
                </tr>
              </thead>
              <tbody>
                @for (item of contents(); track item.id) {
                  <tr>
                    <td>
                      <div class="item-cell">
                        <div class="cover-thumb">
                          @if (item.coverUrl) {
                            <img [src]="item.coverUrl" [alt]="'Carátula de ' + item.title" onerror="this.style.display='none'" />
                          } @else {
                            <span aria-hidden="true">🎬</span>
                          }
                        </div>
                        <div class="item-meta">
                          <div class="title-row">
                            <strong>{{ item.title }}</strong>
                            @if (item.isExclusive) {
                              <span class="badge badge-exclusive" title="Producción propia del catálogo exclusivo">⭐ Exclusiva</span>
                            }
                          </div>
                          <small class="item-sub">
                            {{ item.releaseYear || 'S/A' }} • {{ item.duration || (item.durationMinutes ? item.durationMinutes + ' min' : 'Audio') }} • {{ item.director || 'Director no listado' }}
                          </small>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span class="badge" [ngClass]="getTypeBadgeClass(item.type)">
                        {{ item.type }}
                      </span>
                    </td>
                    <td>
                      <span class="lang-text">{{ item.dubbing || (item.language === 'en' ? 'Inglés' : 'Español') }}</span>
                    </td>
                    <td>
                      @if (item.audioDescription) {
                        <span class="badge badge-success">✓ {{ item.narrator || 'Con AD' }}</span>
                      } @else {
                        <span class="badge badge-warning">Sin AD</span>
                      }
                    </td>
                    <td>
                      @if (item.status === 'DRAFT') {
                        <span class="badge badge-warning">Borrador</span>
                      } @else {
                        <span class="badge badge-success">Publicado</span>
                      }
                    </td>
                    <td>
                      <div style="display: flex; flex-direction: column; gap: 2px;">
                        <strong>👁️ {{ item.viewsCount }}</strong>
                        <span class="rating-badge">⭐ {{ item.averageRating.toFixed(1) }} ({{ item.ratingsCount }})</span>
                      </div>
                    </td>
                    <td>
                      <div class="action-buttons">
                        <a
                          [routerLink]="['/catalog/edit', item.id]"
                          class="btn btn-secondary btn-sm"
                          [attr.aria-label]="'Editar ficha de ' + item.title"
                          title="Editar ficha cinematográfica"
                        >
                          <span aria-hidden="true">✏️</span>
                          <span>Editar</span>
                        </a>
                        <button
                          type="button"
                          class="btn btn-secondary btn-sm"
                          (click)="copyAudioLink(item)"
                          [attr.aria-label]="'Copiar enlace de reproducción de ' + item.title"
                          title="Copiar enlace de streaming"
                        >
                          <span aria-hidden="true">🔗</span>
                        </button>
                        <button
                          type="button"
                          class="btn btn-danger btn-sm"
                          (click)="confirmDelete(item)"
                          [attr.aria-label]="'Eliminar ' + item.title + ' del catálogo'"
                          title="Eliminar del catálogo"
                        >
                          <span aria-hidden="true">🗑️</span>
                        </button>
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <!-- PAGINACIÓN ACCESIBLE -->
          <nav class="pagination-bar" aria-label="Paginación de resultados del catálogo">
            <span role="status" aria-live="polite">Mostrando página {{ currentPage() }} de {{ totalPages() }} ({{ totalItems() | number }} obras en total)</span>
            <div class="page-controls">
              <button
                type="button"
                class="btn btn-secondary btn-sm"
                [disabled]="currentPage() === 1"
                (click)="changePage(currentPage() - 1)"
                aria-label="Ir a la página anterior"
              >
                ← Anterior
              </button>
              <span aria-current="page" style="font-weight: 700; color: var(--text-main);">Página {{ currentPage() }} / {{ totalPages() }}</span>
              <button
                type="button"
                class="btn btn-secondary btn-sm"
                [disabled]="currentPage() >= totalPages()"
                (click)="changePage(currentPage() + 1)"
                aria-label="Ir a la página siguiente"
              >
                Siguiente →
              </button>
            </div>
          </nav>
        </div>
      }
    </div>
  `,
  styles: [`
    .sync-card {
      background: linear-gradient(180deg, rgba(18, 24, 38, 0.98), rgba(13, 18, 31, 0.98));
      border: 1px solid rgba(99, 102, 241, 0.28);
    }

    .sync-badges {
      display: flex;
      gap: 8px;
      align-items: center;
      flex-wrap: wrap;
    }

    .sync-progress-box {
      margin-top: 18px;
      padding: 14px 16px;
      background-color: var(--bg-input);
      border: 1px solid rgba(6, 182, 212, 0.35);
      border-radius: var(--radius-sm);
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .sync-progress-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 13px;
      color: #22d3ee;
    }

    .sync-info-note {
      flex: 1;
      min-width: 260px;
      color: var(--text-muted);
      font-size: 13px;
    }

    .sync-buttons {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
    }

    .filters-card {
      padding: 16px 20px;
    }

    .filters-row {
      display: flex;
      gap: 12px;
      align-items: center;
      flex-wrap: wrap;
    }

    .search-box {
      flex: 1;
      min-width: 260px;
      position: relative;
    }

    .search-icon {
      position: absolute;
      left: 12px;
      top: 50%;
      transform: translateY(-50%);
      font-size: 14px;
      opacity: 0.6;
    }

    .search-box .form-control {
      padding-left: 36px;
    }

    .filter-select {
      min-width: 165px;
    }

    .item-cell {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .cover-thumb {
      width: 44px;
      height: 60px;
      border-radius: 6px;
      background-color: var(--bg-input);
      overflow: hidden;
      flex-shrink: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      border: 1px solid var(--border-color);
    }

    .cover-thumb img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .item-meta {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .title-row {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }

    .item-sub {
      color: var(--text-muted);
      font-size: 12px;
    }

    .lang-text {
      font-size: 13px;
      color: var(--text-muted);
      font-weight: 600;
    }

    .rating-badge {
      font-weight: 700;
      color: #fbbf24;
      font-size: 12px;
    }

    .action-buttons {
      display: flex;
      gap: 6px;
      align-items: center;
    }

    .pagination-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 12px;
      padding-top: 16px;
      font-size: 13px;
      color: var(--text-muted);
    }

    .page-controls {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .loading-card, .empty-card {
      padding: 60px 24px;
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 16px;
    }

    .empty-icon {
      font-size: 48px;
    }
  `],
})
export class CatalogListComponent implements OnInit, OnDestroy {
  private readonly contentService = inject(ContentService);

  readonly contents = signal<ContentItem[]>([]);
  readonly isLoading = signal<boolean>(false);
  readonly currentPage = signal<number>(1);
  readonly totalPages = signal<number>(1);
  readonly totalItems = signal<number>(0);

  readonly showSyncPanel = signal<boolean>(true);
  readonly syncStatus = signal<any | null>(null);
  readonly isCheckingRemote = signal<boolean>(false);
  readonly isSyncing = signal<boolean>(false);
  readonly statusMessage = signal<string | null>(null);
  readonly statusType = signal<'success' | 'error' | 'info'>('info');

  private pollTimer: any = null;

  searchQuery = '';
  selectedType = '';
  selectedStatus = 'ALL';
  selectedExclusive = '';

  ngOnInit() {
    this.loadCatalog();
    this.loadSyncStatus();
  }

  ngOnDestroy() {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
    }
  }

  loadSyncStatus() {
    this.contentService.getCatalogSyncStatus().subscribe({
      next: (status) => {
        this.syncStatus.set(status);
        if (status?.syncProgress?.isRunning) {
          this.isSyncing.set(true);
          this.startProgressPolling();
        } else {
          this.isSyncing.set(false);
        }
      },
      error: () => {},
    });
  }

  private startProgressPolling() {
    if (this.pollTimer) return;
    this.pollTimer = setInterval(() => {
      this.contentService.getCatalogSyncStatus().subscribe({
        next: (status) => {
          this.syncStatus.set(status);
          if (!status?.syncProgress?.isRunning) {
            this.isSyncing.set(false);
            clearInterval(this.pollTimer);
            this.pollTimer = null;
            this.loadCatalog();
            this.notify('✓ Sincronización del catálogo en PostgreSQL completada con éxito.', 'success');
          }
        },
        error: () => {},
      });
    }, 2000);
  }

  getSyncPercentage(): number {
    const prog = this.syncStatus()?.syncProgress;
    if (!prog || !prog.total) return 5;
    return Math.min(100, Math.round((prog.processed / prog.total) * 100));
  }

  checkRemoteCatalog(sendPush: boolean) {
    this.isCheckingRemote.set(true);
    this.notify('Consultando servidor remoto audiocinemateca.com...', 'info');
    this.contentService.checkRemoteCatalogUpdate(sendPush).subscribe({
      next: (res) => {
        this.isCheckingRemote.set(false);
        this.loadSyncStatus();
        this.loadCatalog();
        if (res?.updated) {
          this.notify(
            `¡Catálogo actualizado a la versión ${res.newVersion}! Se añadieron ${res.addedCount || 0} obras nuevas.`,
            'success',
          );
        } else {
          this.notify(
            res?.message || `El catálogo ya se encuentra en la última versión disponible (${res?.currentVersion || 'al día'}).`,
            'success',
          );
        }
      },
      error: (err) => {
        this.isCheckingRemote.set(false);
        this.notify(`Error al consultar servidor remoto: ${err?.error?.message || err?.message || 'Error'}`, 'error');
      },
    });
  }

  syncCatalogNow(sendPush: boolean) {
    this.isSyncing.set(true);
    this.notify('Iniciando sincronización del catálogo maestro hacia PostgreSQL...', 'info');
    this.contentService.syncCatalogToDatabase(sendPush).subscribe({
      next: (res) => {
        this.loadSyncStatus();
        this.startProgressPolling();
        if (res?.message) {
          this.notify(res.message, 'info');
        }
      },
      error: (err) => {
        this.isSyncing.set(false);
        this.notify(`Error al iniciar sincronización: ${err?.error?.message || err?.message || 'Error'}`, 'error');
      },
    });
  }

  loadCatalog() {
    this.isLoading.set(true);
    this.contentService
      .getContents({
        page: this.currentPage(),
        limit: 15,
        search: this.searchQuery || undefined,
        type: this.selectedType || undefined,
        status: this.selectedStatus || 'ALL',
        isExclusive: this.selectedExclusive || undefined,
        sortBy: 'recent',
      })
      .subscribe({
        next: (res) => {
          this.contents.set(res.data);
          this.totalItems.set(res.meta.total);
          this.totalPages.set(res.meta.totalPages || 1);
          this.isLoading.set(false);
        },
        error: () => {
          this.isLoading.set(false);
        },
      });
  }

  applyFilters() {
    this.currentPage.set(1);
    this.loadCatalog();
  }

  changePage(page: number) {
    this.currentPage.set(page);
    this.loadCatalog();
  }

  getTypeBadgeClass(type: string): string {
    switch (type) {
      case 'MOVIE': return 'badge-primary';
      case 'SERIES': return 'badge-info';
      case 'DOCUMENTARY': return 'badge-success';
      case 'SHORT': return 'badge-warning';
      default: return 'badge-primary';
    }
  }

  copyAudioLink(item: ContentItem) {
    const url = item.audioUrl || (item.audioUrls && item.audioUrls[0]) || '';
    if (url && typeof navigator !== 'undefined') {
      navigator.clipboard.writeText(url);
      this.notify(`Enlace de streaming copiado al portapapeles: ${item.title}`, 'success');
    } else {
      this.notify('Esta obra es una serie por capítulos o no tiene URL única directa.', 'info');
    }
  }

  confirmDelete(item: ContentItem) {
    if (confirm(`¿Estás seguro de que deseas eliminar permanentemente la obra "${item.title}"?`)) {
      this.contentService.deleteContent(item.id).subscribe({
        next: () => {
          this.notify(`Obra "${item.title}" eliminada correctamente.`, 'success');
          this.loadCatalog();
          this.loadSyncStatus();
        },
        error: (err) => {
          this.notify(`Error al eliminar: ${err?.error?.message || err?.message || 'Error desconocido'}`, 'error');
        },
      });
    }
  }

  private notify(msg: string, type: 'success' | 'error' | 'info') {
    this.statusMessage.set(msg);
    this.statusType.set(type);
  }
}
