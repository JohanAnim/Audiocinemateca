import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ContentService } from '../../core/services/content.service';
import { CreateContentDto, ContentType, SeasonDto } from '../../core/models/content.models';

@Component({
  selector: 'app-catalog-create',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  template: `
    <div class="create-page">
      <div class="page-header">
        <div>
          <div class="breadcrumbs">
            <a routerLink="/catalog">Catálogo</a>
            <span>/</span>
            <span>Nueva Ficha Exclusiva</span>
          </div>
          <h1>Ficha Completa de Producción & Catálogo</h1>
          <p class="subtitle">Creación de títulos con audiodescripción, doblaje multi-idioma y pistas de streaming</p>
        </div>
        <div class="header-actions">
          <a routerLink="/catalog" class="btn btn-secondary">Cancelar</a>
          <button class="btn btn-primary" (click)="saveContent('PUBLISHED')" [disabled]="isSaving()">
            @if (isSaving()) {
              <span class="spinner"></span>
              <span>Guardando...</span>
            } @else {
              <span>🚀 Publicar en Catálogo</span>
            }
          </button>
        </div>
      </div>

      @if (errorMessage()) {
        <div class="alert-error" role="alert">
          <span>⚠️ {{ errorMessage() }}</span>
        </div>
      }

      <div class="form-layout">
        <!-- COLUMNA PRINCIPAL (FORMULARIO) -->
        <div class="form-main">
          <!-- 1. IDENTIFICACIÓN Y TIPO -->
          <div class="card section-card">
            <div class="section-title">
              <span class="step-num">1</span>
              <div>
                <h3>Identificación & Formato</h3>
                <small>Tipo de producción, exclusividad e idiomas disponibles</small>
              </div>
            </div>

            <div class="form-row">
              <div class="form-group flex-2">
                <label class="form-label">Título de la Obra *</label>
                <input
                  type="text"
                  class="form-control"
                  placeholder="Ej. Los Sonidos del Silencio"
                  [(ngModel)]="form.title"
                  (input)="generateSlug()"
                  required
                />
              </div>

              <div class="form-group flex-1">
                <label class="form-label">Tipo de Contenido *</label>
                <select class="form-control" [(ngModel)]="form.type">
                  <option value="MOVIE">Película</option>
                  <option value="SERIES">Serie de Televisión</option>
                  <option value="DOCUMENTARY">Documental</option>
                  <option value="SHORT">Cortometraje</option>
                </select>
              </div>
            </div>

            <!-- EXCLUSIVIDAD Y LENGUAJE -->
            <div class="highlight-box">
              <div class="checkbox-row">
                <input
                  type="checkbox"
                  id="isExclusive"
                  [(ngModel)]="form.isExclusive"
                  class="custom-checkbox"
                />
                <label for="isExclusive" class="checkbox-label">
                  <strong>⭐ Marcar como Producción Exclusiva de Audiocinemateca</strong>
                  <span>Tendrá insignia dorada destacada y prioridad en recomendaciones del feed</span>
                </label>
              </div>
            </div>

            <div class="form-row mt-3">
              <div class="form-group flex-1">
                <label class="form-label">Idioma / Doblaje del Audio *</label>
                <select class="form-control" [(ngModel)]="form.dubbing">
                  <option value="Español Latino">Español Latino (Latinoamérica)</option>
                  <option value="Español de España (Castellano)">Español de España (Castellano)</option>
                  <option value="Inglés (English)">Inglés Original (English)</option>
                  <option value="Audio Original con AD en Español">Audio Original con AD en Español</option>
                  <option value="Bilingüe / Multi-idioma">Bilingüe / Multi-idioma</option>
                </select>
              </div>

              <div class="form-group flex-1">
                <label class="form-label">Código de Idioma Base</label>
                <select class="form-control" [(ngModel)]="form.language">
                  <option value="es">Español (es)</option>
                  <option value="en">Inglés (en)</option>
                  <option value="fr">Francés (fr)</option>
                  <option value="pt">Portugués (pt)</option>
                </select>
              </div>
            </div>

            <div class="form-row">
              <div class="form-group flex-1">
                <label class="form-label">¿Tiene Audiodescripción?</label>
                <div class="toggle-group">
                  <button
                    type="button"
                    class="btn btn-sm"
                    [class.btn-primary]="form.audioDescription"
                    [class.btn-secondary]="!form.audioDescription"
                    (click)="form.audioDescription = true"
                  >
                    ✓ Sí (Accesible)
                  </button>
                  <button
                    type="button"
                    class="btn btn-sm"
                    [class.btn-danger]="!form.audioDescription"
                    [class.btn-secondary]="form.audioDescription"
                    (click)="form.audioDescription = false"
                  >
                    ✕ No
                  </button>
                </div>
              </div>

              <div class="form-group flex-2">
                <label class="form-label">Narrador(a) de la Audiodescripción</label>
                <input
                  type="text"
                  class="form-control"
                  placeholder="Ej. Johan Gutiérrez, Voz Institucional ONCE, Aura IA..."
                  [(ngModel)]="form.narrator"
                />
              </div>
            </div>

            <div class="form-row">
              <div class="form-group flex-1">
                <label class="form-label">Año de Lanzamiento</label>
                <input type="number" class="form-control" placeholder="2026" [(ngModel)]="form.releaseYear" />
              </div>

              <div class="form-group flex-1">
                <label class="form-label">Duración (Texto)</label>
                <input type="text" class="form-control" placeholder="1h 45m" [(ngModel)]="form.duration" />
              </div>

              <div class="form-group flex-1">
                <label class="form-label">País de Origen</label>
                <input type="text" class="form-control" placeholder="Colombia, España, EE. UU..." [(ngModel)]="form.country" />
              </div>
            </div>
          </div>

          <!-- 2. SINOPSIS & ACCESIBILIDAD SONORA -->
          <div class="card section-card">
            <div class="section-title">
              <span class="step-num">2</span>
              <div>
                <h3>Sinopsis & Accesibilidad Sonora</h3>
                <small>Textos descriptivos para la ficha y narración de TalkBack / Aura</small>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">Sinopsis Cinematográfica *</label>
              <textarea
                class="form-control"
                rows="4"
                placeholder="Escribe la sinopsis detallada del argumento cinematográfico..."
                [(ngModel)]="form.synopsis"
                required
              ></textarea>
            </div>

            <div class="form-group">
              <label class="form-label">Género(s) / Etiquetas</label>
              <input
                type="text"
                class="form-control"
                placeholder="Ej. Ciencia Ficción, Suspenso, Drama, Cine Accesible (separados por coma)"
                [(ngModel)]="genreInput"
              />
            </div>
          </div>

          <!-- 3. FICHA TÉCNICA Y ARTÍSTICA -->
          <div class="card section-card">
            <div class="section-title">
              <span class="step-num">3</span>
              <div>
                <h3>Ficha Técnica & Equipo Artístico</h3>
                <small>Metadatos para el buscador de directores, narradores y reparto</small>
              </div>
            </div>

            <div class="form-row">
              <div class="form-group flex-1">
                <label class="form-label">Director(es)</label>
                <input type="text" class="form-control" placeholder="Ej. Christopher Nolan" [(ngModel)]="form.director" />
              </div>

              <div class="form-group flex-1">
                <label class="form-label">Guion / Guionista</label>
                <input type="text" class="form-control" placeholder="Ej. Jonathan Nolan" [(ngModel)]="form.screenplay" />
              </div>
            </div>

            <div class="form-row">
              <div class="form-group flex-1">
                <label class="form-label">Banda Sonora / Música</label>
                <input type="text" class="form-control" placeholder="Ej. Hans Zimmer" [(ngModel)]="form.music" />
              </div>

              <div class="form-group flex-1">
                <label class="form-label">Reparto Principal</label>
                <input type="text" class="form-control" placeholder="Ej. Matthew McConaughey, Anne Hathaway" [(ngModel)]="form.cast" />
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">URL Externa de Referencia (FilmAffinity o IMDb)</label>
              <input type="text" class="form-control" placeholder="https://www.filmaffinity.com/es/film..." [(ngModel)]="form.filmaffinityUrl" />
            </div>
          </div>

          <!-- 4. PISTAS DE AUDIO & STREAMING -->
          <div class="card section-card">
            <div class="section-title">
              <span class="step-num">4</span>
              <div>
                <h3>Pistas de Audio & Streaming Directo</h3>
                <small>Enlaces de audio reproducibles en ExoPlayer, Web y Google Cast</small>
              </div>
            </div>

            @if (form.type !== 'SERIES') {
              <!-- MODO PELÍCULA / CORTO / DOCUMENTAL -->
              <div class="form-group">
                <label class="form-label">URL de Audio Streaming Principal *</label>
                <input
                  type="text"
                  class="form-control"
                  placeholder="https://servidor.com/audios/pelicula.mp3"
                  [(ngModel)]="form.audioUrl"
                  required
                />
              </div>

              <div class="form-group">
                <label class="form-label">Partes Adicionales (Película Multi-Parte)</label>
                <div class="multi-part-inputs">
                  @for (part of multiPartUrls; track $index) {
                    <div class="part-row">
                      <span class="part-num">Parte {{ $index + 1 }}:</span>
                      <input type="text" class="form-control" [(ngModel)]="multiPartUrls[$index]" placeholder="https://..." />
                      <button type="button" class="btn btn-danger btn-sm" (click)="removePart($index)">✕</button>
                    </div>
                  }
                  <button type="button" class="btn btn-secondary btn-sm mt-2" (click)="addPart()">
                    ➕ Agregar Parte Adicional
                  </button>
                </div>
              </div>
            } @else {
              <!-- MODO SERIE: CONSTRUCTOR DE TEMPORADAS Y EPISODIOS -->
              <div class="series-builder">
                <div class="builder-header">
                  <strong>Estructura de Temporadas y Capítulos</strong>
                  <button type="button" class="btn btn-primary btn-sm" (click)="addSeason()">
                    ➕ Nueva Temporada
                  </button>
                </div>

                @for (season of seasons; track season.seasonNumber; let sIndex = $index) {
                  <div class="season-block">
                    <div class="season-head">
                      <h4>Temporada {{ season.seasonNumber }}</h4>
                      <div class="season-actions">
                        <button type="button" class="btn btn-secondary btn-sm" (click)="addEpisode(season)">
                          ➕ Agregar Episodio
                        </button>
                        @if (seasons.length > 1) {
                          <button type="button" class="btn btn-danger btn-sm" (click)="removeSeason(sIndex)">
                            Eliminar Temporada
                          </button>
                        }
                      </div>
                    </div>

                    <div class="episodes-list">
                      @for (ep of season.episodes; track ep.episodeNumber; let eIndex = $index) {
                        <div class="episode-row">
                          <span class="ep-num">E{{ ep.episodeNumber }}</span>
                          <input
                            type="text"
                            class="form-control ep-title"
                            placeholder="Título del Capítulo"
                            [(ngModel)]="ep.title"
                          />
                          <input
                            type="text"
                            class="form-control ep-url"
                            placeholder="URL Audio Streaming (mp3/m4a)"
                            [(ngModel)]="ep.audioUrl"
                          />
                          <input
                            type="number"
                            class="form-control ep-duration"
                            placeholder="Min"
                            [(ngModel)]="ep.durationMinutes"
                            title="Duración en minutos"
                          />
                          <button type="button" class="btn btn-danger btn-sm" (click)="removeEpisode(season, eIndex)">✕</button>
                        </div>
                      }
                    </div>
                  </div>
                }
              </div>
            }
          </div>
        </div>

        <!-- COLUMNA LATERAL (IMÁGENES Y PREVIEW) -->
        <div class="form-sidebar">
          <!-- MATERIAL GRÁFICO & PREVIEWS -->
          <div class="card preview-card">
            <h3>🖼️ Material Gráfico</h3>
            
            <div class="form-group mt-3">
              <label class="form-label">URL de Portada (Vertical)</label>
              <input type="text" class="form-control" placeholder="https://.../cover.jpg" [(ngModel)]="form.coverUrl" />
            </div>

            <div class="cover-preview-box">
              @if (form.coverUrl) {
                <img [src]="form.coverUrl" alt="Previsualización de Portada" onerror="this.style.display='none'" />
              } @else {
                <div class="cover-placeholder">
                  <span>🎬</span>
                  <small>Previsualización de Portada</small>
                </div>
              }
            </div>

            <div class="form-group mt-3">
              <label class="form-label">URL de Banner (Horizontal)</label>
              <input type="text" class="form-control" placeholder="https://.../banner.jpg" [(ngModel)]="form.bannerUrl" />
            </div>

            <div class="banner-preview-box">
              @if (form.bannerUrl) {
                <img [src]="form.bannerUrl" alt="Previsualización de Banner" onerror="this.style.display='none'" />
              } @else {
                <div class="banner-placeholder">
                  <span>🖼️ Previsualización de Banner</span>
                </div>
              }
            </div>
          </div>

          <!-- ESTADO DE PUBLICACIÓN -->
          <div class="card status-card">
            <h3>Visibilidad</h3>
            <div class="form-group mt-2">
              <select class="form-control" [(ngModel)]="form.status">
                <option value="PUBLISHED">Publicado (Visible en App)</option>
                <option value="DRAFT">Borrador (Oculto)</option>
              </select>
            </div>
            <button class="btn btn-primary btn-block" (click)="saveContent('PUBLISHED')" [disabled]="isSaving()">
              🚀 Publicar Inmediatamente
            </button>
            <button class="btn btn-secondary btn-block mt-2" (click)="saveContent('DRAFT')" [disabled]="isSaving()">
              💾 Guardar como Borrador
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .create-page {
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

    .breadcrumbs {
      display: flex;
      gap: 8px;
      font-size: 13px;
      color: var(--text-muted);
      margin-bottom: 4px;
    }

    h1 {
      font-size: 24px;
      font-weight: 800;
    }

    .subtitle {
      font-size: 14px;
      color: var(--text-muted);
    }

    .header-actions {
      display: flex;
      gap: 12px;
    }

    .alert-error {
      background: rgba(239, 68, 68, 0.15);
      border: 1px solid rgba(239, 68, 68, 0.3);
      color: #fca5a5;
      padding: 14px 18px;
      border-radius: var(--radius-sm);
      font-size: 14px;
    }

    .form-layout {
      display: grid;
      grid-template-columns: 1fr 340px;
      gap: 24px;
      align-items: start;
    }

    @media (max-width: 1024px) {
      .form-layout {
        grid-template-columns: 1fr;
      }
    }

    .form-main {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .section-card {
      padding: 24px;
    }

    .section-title {
      display: flex;
      align-items: center;
      gap: 14px;
      margin-bottom: 20px;
      padding-bottom: 14px;
      border-bottom: 1px solid var(--border-color);
    }

    .step-num {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background-color: var(--primary);
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 800;
      font-size: 14px;
    }

    .section-title h3 {
      font-size: 16px;
      font-weight: 700;
      color: var(--text-main);
    }

    .section-title small {
      font-size: 12px;
      color: var(--text-muted);
    }

    .form-row {
      display: flex;
      gap: 16px;
      align-items: flex-start;
    }

    .flex-1 { flex: 1; }
    .flex-2 { flex: 2; }
    .mt-2 { margin-top: 8px; }
    .mt-3 { margin-top: 12px; }

    .highlight-box {
      background: linear-gradient(135deg, rgba(99, 102, 241, 0.12), rgba(245, 158, 11, 0.08));
      border: 1px solid rgba(99, 102, 241, 0.3);
      padding: 16px;
      border-radius: var(--radius-sm);
      margin: 14px 0;
    }

    .checkbox-row {
      display: flex;
      align-items: flex-start;
      gap: 12px;
    }

    .custom-checkbox {
      width: 20px;
      height: 20px;
      margin-top: 2px;
      cursor: pointer;
    }

    .checkbox-label {
      display: flex;
      flex-direction: column;
      cursor: pointer;
    }

    .checkbox-label strong {
      font-size: 14px;
      color: var(--text-main);
    }

    .checkbox-label span {
      font-size: 12px;
      color: var(--text-muted);
    }

    .toggle-group {
      display: flex;
      gap: 8px;
    }

    .multi-part-inputs {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .part-row {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .part-num {
      font-size: 13px;
      font-weight: 700;
      color: var(--text-muted);
      width: 70px;
    }

    /* --- SERIES BUILDER --- */
    .series-builder {
      display: flex;
      flex-direction: column;
      gap: 18px;
    }

    .builder-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 8px;
    }

    .season-block {
      background-color: var(--bg-input);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-sm);
      padding: 16px;
    }

    .season-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;
    }

    .season-head h4 {
      font-size: 15px;
      font-weight: 700;
    }

    .season-actions {
      display: flex;
      gap: 8px;
    }

    .episodes-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .episode-row {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .ep-num {
      font-weight: 800;
      color: var(--primary);
      width: 32px;
      font-size: 13px;
    }

    .ep-title { flex: 2; }
    .ep-url { flex: 3; }
    .ep-duration { width: 75px; }

    /* --- SIDEBAR DE PREVIEWS --- */
    .form-sidebar {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .cover-preview-box {
      width: 100%;
      height: 280px;
      border-radius: var(--radius-sm);
      background-color: var(--bg-input);
      border: 1px dashed var(--border-color);
      overflow: hidden;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-top: 10px;
    }

    .cover-preview-box img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .cover-placeholder, .banner-placeholder {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      color: var(--text-dim);
      font-size: 13px;
    }

    .cover-placeholder span { font-size: 32px; }

    .banner-preview-box {
      width: 100%;
      height: 120px;
      border-radius: var(--radius-sm);
      background-color: var(--bg-input);
      border: 1px dashed var(--border-color);
      overflow: hidden;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-top: 10px;
    }

    .banner-preview-box img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .btn-block {
      width: 100%;
    }
  `],
})
export class CatalogCreateComponent {
  private readonly contentService = inject(ContentService);
  private readonly router = inject(Router);

  isSaving = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  genreInput = '';
  multiPartUrls: string[] = [];

  seasons: SeasonDto[] = [
    {
      seasonNumber: 1,
      title: 'Temporada 1',
      episodes: [
        { episodeNumber: 1, title: 'Episodio 1', audioUrl: '', durationMinutes: 45 },
        { episodeNumber: 2, title: 'Episodio 2', audioUrl: '', durationMinutes: 45 },
      ],
    },
  ];

  form: CreateContentDto = {
    title: '',
    synopsis: '',
    type: 'MOVIE',
    status: 'PUBLISHED',
    dubbing: 'Español Latino',
    language: 'es',
    isExclusive: true,
    audioDescription: true,
    narrator: 'Audiocinemateca Oficial',
    releaseYear: 2026,
    duration: '',
    country: '',
    director: '',
    screenplay: '',
    music: '',
    cast: '',
    coverUrl: '',
    bannerUrl: '',
    audioUrl: '',
    filmaffinityUrl: '',
  };

  generateSlug() {
    // Generación reactiva de slug en tiempo real
  }

  addPart() {
    this.multiPartUrls.push('');
  }

  removePart(index: number) {
    this.multiPartUrls.splice(index, 1);
  }

  addSeason() {
    const nextSeasonNum = this.seasons.length + 1;
    this.seasons.push({
      seasonNumber: nextSeasonNum,
      title: `Temporada ${nextSeasonNum}`,
      episodes: [
        { episodeNumber: 1, title: 'Episodio 1', audioUrl: '', durationMinutes: 45 },
      ],
    });
  }

  removeSeason(index: number) {
    this.seasons.splice(index, 1);
  }

  addEpisode(season: SeasonDto) {
    const nextEpNum = season.episodes.length + 1;
    season.episodes.push({
      episodeNumber: nextEpNum,
      title: `Episodio ${nextEpNum}`,
      audioUrl: '',
      durationMinutes: 45,
    });
  }

  removeEpisode(season: SeasonDto, epIndex: number) {
    season.episodes.splice(epIndex, 1);
  }

  saveContent(status: 'PUBLISHED' | 'DRAFT') {
    if (!this.form.title?.trim()) {
      this.errorMessage.set('El título de la obra es requerido.');
      return;
    }

    if (!this.form.synopsis?.trim()) {
      this.errorMessage.set('La sinopsis de la obra es requerida.');
      return;
    }

    if (this.form.type !== 'SERIES' && !this.form.audioUrl?.trim() && this.multiPartUrls.length === 0) {
      this.errorMessage.set('Debes proporcionar al menos una URL de audio streaming.');
      return;
    }

    this.isSaving.set(true);
    this.errorMessage.set(null);

    const categories = this.genreInput
      .split(',')
      .map((g) => g.trim())
      .filter((g) => g.length > 0);

    const payload: CreateContentDto = {
      ...this.form,
      status,
      categories: categories.length > 0 ? categories : undefined,
      genreRaw: this.genreInput.trim() || undefined,
      audioUrls: this.multiPartUrls.filter((u) => u.trim().length > 0),
      seasons: this.form.type === 'SERIES' ? this.seasons : undefined,
    };

    this.contentService.createContent(payload).subscribe({
      next: (created) => {
        this.isSaving.set(false);
        alert(`¡Obra "${created.title}" creada y publicada con éxito en el catálogo!`);
        this.router.navigate(['/catalog']);
      },
      error: (err) => {
        this.isSaving.set(false);
        const msg = err?.error?.message || err?.message || 'Error guardando contenido.';
        this.errorMessage.set(Array.isArray(msg) ? msg.join(', ') : msg);
      },
    });
  }
}
