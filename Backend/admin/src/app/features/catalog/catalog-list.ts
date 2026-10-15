import { Component, inject, signal, OnInit } from '@angular/core';
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
    <div class="catalog-page">
      <div class="page-header">
        <div>
          <h1>Catálogo & Producciones Exclusivas</h1>
          <p class="subtitle">Administra todas las películas, series, documentales y cortos accesibles</p>
        </div>
        <a routerLink="/catalog/create" class="btn btn-primary">
          <span>✨</span>
          <span>Nueva Ficha Exclusiva</span>
        </a>
      </div>

      <!-- BARRA DE BÚSQUEDA Y FILTROS -->
      <div class="card filters-card">
        <div class="filters-row">
          <div class="search-box">
            <span class="search-icon">🔍</span>
            <input
              type="text"
              class="form-control"
              placeholder="Buscar por título, director, narrador o género..."
              [(ngModel)]="searchQuery"
              (keyup.enter)="applyFilters()"
            />
          </div>

          <select class="form-control type-select" [(ngModel)]="selectedType" (change)="applyFilters()">
            <option value="">Todos los tipos</option>
            <option value="MOVIE">Películas</option>
            <option value="SERIES">Series</option>
            <option value="DOCUMENTARY">Documentales</option>
            <option value="SHORT">Cortometrajes</option>
          </select>

          <button class="btn btn-secondary" (click)="applyFilters()">Filtrar</button>
        </div>
      </div>

      <!-- LISTA DE CONTENIDO -->
      @if (isLoading()) {
        <div class="card loading-card">
          <div class="spinner"></div>
          <p>Cargando catálogo cinematográfico...</p>
        </div>
      } @else if (contents().length === 0) {
        <div class="card empty-card">
          <span class="empty-icon">🎬</span>
          <h3>No se encontraron obras</h3>
          <p>Prueba con otros términos de búsqueda o añade una nueva ficha al catálogo.</p>
          <a routerLink="/catalog/create" class="btn btn-primary">Crear Primera Ficha Exclusiva</a>
        </div>
      } @else {
        <div class="card table-card">
          <div class="table-responsive">
            <table class="table">
              <thead>
                <tr>
                  <th>Obra</th>
                  <th>Tipo</th>
                  <th>Idioma / Doblaje</th>
                  <th>Audiodescripción</th>
                  <th>Vistas</th>
                  <th>Valoración</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                @for (item of contents(); track item.id) {
                  <tr>
                    <td>
                      <div class="item-cell">
                        <div class="cover-thumb">
                          @if (item.coverUrl) {
                            <img [src]="item.coverUrl" [alt]="item.title" onerror="this.style.display='none'" />
                          } @else {
                            <span>🎬</span>
                          }
                        </div>
                        <div class="item-meta">
                          <div class="title-row">
                            <strong>{{ item.title }}</strong>
                            @if (item.isExclusive) {
                              <span class="badge badge-exclusive">⭐ Exclusiva</span>
                            }
                          </div>
                          <small class="item-sub">
                            {{ item.releaseYear || 'S/A' }} • {{ item.duration || (item.durationMinutes ? item.durationMinutes + ' min' : '') }} • {{ item.director || 'Director no listado' }}
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
                      <span class="lang-text">{{ item.dubbing || (item.language === 'en' ? 'Inglés' : 'Español Latino') }}</span>
                    </td>
                    <td>
                      @if (item.audioDescription) {
                        <span class="badge badge-success">✓ Sí ({{ item.narrator || 'Voz Nativa' }})</span>
                      } @else {
                        <span class="badge badge-warning">Sin AD</span>
                      }
                    </td>
                    <td>
                      <strong>{{ item.viewsCount }}</strong>
                    </td>
                    <td>
                      <span class="rating-badge">⭐ {{ item.averageRating.toFixed(1) }} ({{ item.ratingsCount }})</span>
                    </td>
                    <td>
                      <div class="action-buttons">
                        <button class="btn btn-secondary btn-sm" (click)="copyAudioLink(item)" title="Copiar enlace de streaming">
                          🔗
                        </button>
                        <button class="btn btn-danger btn-sm" (click)="confirmDelete(item)" title="Eliminar del catálogo">
                          🗑️
                        </button>
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <!-- PAGINACIÓN -->
          <div class="pagination-bar">
            <span>Total: {{ totalItems() }} obras encontradas</span>
            <div class="page-controls">
              <button class="btn btn-secondary btn-sm" [disabled]="currentPage() === 1" (click)="changePage(currentPage() - 1)">
                Anterior
              </button>
              <span>Página {{ currentPage() }} de {{ totalPages() }}</span>
              <button class="btn btn-secondary btn-sm" [disabled]="currentPage() >= totalPages()" (click)="changePage(currentPage() + 1)">
                Siguiente
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .catalog-page {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .page-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 16px;
    }

    h1 {
      font-size: 24px;
      font-weight: 800;
    }

    .subtitle {
      font-size: 14px;
      color: var(--text-muted);
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
      min-width: 280px;
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

    .type-select {
      width: 180px;
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
      font-size: 13px;
    }

    .action-buttons {
      display: flex;
      gap: 6px;
    }

    .pagination-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
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
      padding: 60px;
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
export class CatalogListComponent implements OnInit {
  private readonly contentService = inject(ContentService);

  readonly contents = signal<ContentItem[]>([]);
  readonly isLoading = signal<boolean>(false);
  readonly currentPage = signal<number>(1);
  readonly totalPages = signal<number>(1);
  readonly totalItems = signal<number>(0);

  searchQuery = '';
  selectedType = '';

  ngOnInit() {
    this.loadCatalog();
  }

  loadCatalog() {
    this.isLoading.set(true);
    this.contentService
      .getContents({
        page: this.currentPage(),
        limit: 15,
        search: this.searchQuery,
        type: this.selectedType || undefined,
        sortBy: 'recent',
      })
      .subscribe({
        next: (res) => {
          this.contents.set(res.data);
          this.totalItems.set(res.meta.total);
          this.totalPages.set(res.meta.totalPages);
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
      default: return 'badge-secondary';
    }
  }

  copyAudioLink(item: ContentItem) {
    const url = item.audioUrl || (item.audioUrls && item.audioUrls[0]) || '';
    if (url && typeof navigator !== 'undefined') {
      navigator.clipboard.writeText(url);
      alert(`Enlace copiado al portapapeles: ${url}`);
    } else {
      alert('Esta obra no tiene una URL de audio directa asociada');
    }
  }

  confirmDelete(item: ContentItem) {
    if (confirm(`¿Estás seguro de que deseas eliminar permanentemente la obra "${item.title}"?`)) {
      this.contentService.deleteContent(item.id).subscribe({
        next: () => {
          this.loadCatalog();
        },
        error: (err) => {
          alert(`Error al eliminar: ${err?.message || 'Error desconocido'}`);
        },
      });
    }
  }
}
