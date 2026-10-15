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
          <nav class="breadcrumbs" aria-label="Ruta de navegación">
            <a routerLink="/catalog">Catálogo</a>
            <span aria-hidden="true">/</span>
            <span aria-current="page">Nueva Ficha Exclusiva</span>
          </nav>
          <h1>Ficha Completa de Producción & Catálogo</h1>
          <p class="subtitle">Creación de títulos con audiodescripción, doblaje multi-idioma y pistas de streaming</p>
        </div>
        <div class="header-actions">
          <a routerLink="/catalog" class="btn btn-secondary cursor-pointer">Cancelar</a>
          <button class="btn btn-primary cursor-pointer" (click)="saveContent()" [disabled]="isSaving()">
            @if (isSaving()) {
              <span class="spinner" aria-hidden="true"></span>
              <span>Guardando...</span>
            } @else {
              <span>💾 Guardar Ficha Cinematográfica</span>
            }
          </button>
        </div>
      </div>

      @if (errorMessage()) {
        <div class="alert-error" role="alert" aria-live="assertive">
          <span>⚠️ {{ errorMessage() }}</span>
        </div>
      }

      <div class="form-layout">
        <!-- COLUMNA PRINCIPAL (FORMULARIO) -->
        <div class="form-main">
          <!-- 1. IDENTIFICACIÓN Y TIPO -->
          <section class="card section-card" aria-labelledby="section-1-title">
            <div class="section-title">
              <span class="step-num" aria-hidden="true">1</span>
              <div>
                <h2 id="section-1-title">Identificación & Formato</h2>
                <small>Tipo de producción, exclusividad e idiomas disponibles</small>
              </div>
            </div>

            <div class="form-row">
              <div class="form-group flex-2">
                <label for="content-title" class="form-label">Título de la Obra *</label>
                <input
                  id="content-title"
                  type="text"
                  class="form-control"
                  placeholder="Ej. Los Sonidos del Silencio"
                  [(ngModel)]="form.title"
                  (input)="generateSlug()"
                  required
                  aria-required="true"
                />
              </div>

              <div class="form-group flex-1">
                <label for="content-type" class="form-label">Tipo de Contenido *</label>
                <select id="content-type" class="form-control" [(ngModel)]="form.type" required aria-required="true">
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
                  id="content-is-exclusive"
                  [(ngModel)]="form.isExclusive"
                  class="custom-checkbox"
                />
                <label for="content-is-exclusive" class="checkbox-label">
                  <strong>⭐ Marcar como Producción Exclusiva de Audiocinemateca</strong>
                  <span>Tendrá insignia dorada destacada y prioridad en recomendaciones del feed</span>
                </label>
              </div>
            </div>

            <div class="form-row mt-3">
              <div class="form-group flex-1">
                <label for="content-dubbing" class="form-label">Idioma / Doblaje del Audio *</label>
                <select id="content-dubbing" class="form-control" [(ngModel)]="form.dubbing" required aria-required="true">
                  <option value="Español Latino">Español Latino (Latinoamérica)</option>
                  <option value="Español de España (Castellano)">Español de España (Castellano)</option>
                  <option value="Inglés (English)">Inglés Original (English)</option>
                  <option value="Audio Original con AD en Español">Audio Original con AD en Español</option>
                  <option value="Bilingüe / Multi-idioma">Bilingüe / Multi-idioma</option>
                </select>
              </div>

              <div class="form-group flex-1">
                <label for="content-language" class="form-label">Código de Idioma Base</label>
                <select id="content-language" class="form-control" [(ngModel)]="form.language">
                  <option value="es">Español (es)</option>
                  <option value="en">Inglés (en)</option>
                  <option value="fr">Francés (fr)</option>
                  <option value="pt">Portugués (pt)</option>
                </select>
              </div>
            </div>

            <div class="form-row">
              <div class="form-group flex-1">
                <label for="content-audio-description" class="form-label">Audiodescripción *</label>
                <select id="content-audio-description" class="form-control" [(ngModel)]="form.audioDescription">
                  <option [ngValue]="true">✓ Sí (Producción Accesible con Audiodescripción)</option>
                  <option [ngValue]="false">✕ No (Sin pista de audiodescripción)</option>
                </select>
              </div>

              <div class="form-group flex-2">
                <label for="content-narrator" class="form-label">Narrador(a) de la Audiodescripción</label>
                <input
                  id="content-narrator"
                  type="text"
                  class="form-control"
                  placeholder="Ej. Johan Gutiérrez, Voz Institucional ONCE, Aura IA..."
                  [(ngModel)]="form.narrator"
                />
              </div>
            </div>

            <div class="form-row">
              <div class="form-group flex-1">
                <label for="content-release-year" class="form-label">Año de Lanzamiento</label>
                <input id="content-release-year" type="number" class="form-control" placeholder="2026" [(ngModel)]="form.releaseYear" />
              </div>

              <div class="form-group flex-1">
                <label for="content-duration" class="form-label">Duración Estimada</label>
                <input id="content-duration" type="text" class="form-control" placeholder="1h 45m" [(ngModel)]="form.duration" />
              </div>

              <div class="form-group flex-1">
                <label for="content-country" class="form-label">País de Origen</label>
                <input id="content-country" type="text" class="form-control" placeholder="Colombia, España, EE. UU..." [(ngModel)]="form.country" />
              </div>
            </div>
          </section>

          <!-- 2. SINOPSIS & ACCESIBILIDAD SONORA -->
          <section class="card section-card" aria-labelledby="section-2-title">
            <div class="section-title">
              <span class="step-num" aria-hidden="true">2</span>
              <div>
                <h2 id="section-2-title">Sinopsis & Accesibilidad Sonora</h2>
                <small>Textos descriptivos para la ficha y narración de TalkBack / Aura</small>
              </div>
            </div>

            <div class="form-group">
              <label for="content-synopsis" class="form-label">Sinopsis Cinematográfica *</label>
              <textarea
                id="content-synopsis"
                class="form-control"
                rows="4"
                placeholder="Escribe la sinopsis detallada del argumento cinematográfico..."
                [(ngModel)]="form.synopsis"
                required
                aria-required="true"
              ></textarea>
            </div>

            <div class="form-group">
              <label for="content-genres" class="form-label">Género(s) / Categorías (separados por coma)</label>
              <input
                id="content-genres"
                type="text"
                class="form-control"
                placeholder="Ej. Ciencia Ficción, Suspenso, Drama, Cine Accesible"
                [(ngModel)]="genreInput"
              />
            </div>
          </section>

          <!-- 3. FICHA TÉCNICA Y ARTÍSTICA -->
          <section class="card section-card" aria-labelledby="section-3-title">
            <div class="section-title">
              <span class="step-num" aria-hidden="true">3</span>
              <div>
                <h2 id="section-3-title">Ficha Técnica & Equipo Artístico</h2>
                <small>Metadatos para el buscador de directores, narradores y reparto</small>
              </div>
            </div>

            <div class="form-row">
              <div class="form-group flex-1">
                <label for="content-director" class="form-label">Director(es)</label>
                <input id="content-director" type="text" class="form-control" placeholder="Ej. Christopher Nolan" [(ngModel)]="form.director" />
              </div>

              <div class="form-group flex-1">
                <label for="content-screenplay" class="form-label">Guion / Guionista</label>
                <input id="content-screenplay" type="text" class="form-control" placeholder="Ej. Jonathan Nolan" [(ngModel)]="form.screenplay" />
              </div>
            </div>

            <div class="form-row">
              <div class="form-group flex-1">
                <label for="content-music" class="form-label">Banda Sonora / Música</label>
                <input id="content-music" type="text" class="form-control" placeholder="Ej. Hans Zimmer" [(ngModel)]="form.music" />
              </div>

              <div class="form-group flex-1">
                <label for="content-cast" class="form-label">Reparto Principal</label>
                <input id="content-cast" type="text" class="form-control" placeholder="Ej. Matthew McConaughey, Anne Hathaway" [(ngModel)]="form.cast" />
              </div>
            </div>

            <div class="form-group">
              <label for="content-filmaffinity" class="form-label">URL Externa de Referencia (FilmAffinity o IMDb)</label>
              <input id="content-filmaffinity" type="text" class="form-control" placeholder="https://www.filmaffinity.com/es/film..." [(ngModel)]="form.filmaffinityUrl" />
            </div>
          </section>

          <!-- 4. PISTAS DE AUDIO & STREAMING -->
          <section class="card section-card" aria-labelledby="section-4-title">
            <div class="section-title">
              <span class="step-num" aria-hidden="true">4</span>
              <div>
                <h2 id="section-4-title">Pistas de Audio & Streaming Directo</h2>
                <small>Enlaces de audio reproducibles en ExoPlayer, Web y Google Cast</small>
              </div>
            </div>

            @if (form.type !== 'SERIES') {
              <!-- MODO PELÍCULA / CORTO / DOCUMENTAL -->
              <div class="form-group">
                <label for="content-audio-url" class="form-label">URL de Audio Streaming Principal *</label>
                <input
                  id="content-audio-url"
                  type="text"
                  class="form-control"
                  placeholder="https://servidor.com/audios/pelicula.mp3"
                  [(ngModel)]="form.audioUrl"
                  required
                  aria-required="true"
                />
              </div>

              <div class="form-group">
                <label class="form-label">Partes Adicionales (Película Multi-Parte)</label>
                <div class="multi-part-inputs">
                  @for (part of multiPartUrls; track $index) {
                    <div class="part-row">
                      <label [for]="'content-part-' + $index" class="part-num">Parte {{ $index + 1 }}:</label>
                      <input
                        [id]="'content-part-' + $index"
                        type="text"
                        class="form-control"
                        [(ngModel)]="multiPartUrls[$index]"
                        placeholder="https://..."
                        [attr.aria-label]="'URL de la parte adicional ' + ($index + 1)"
                      />
                      <button
                        type="button"
                        class="btn btn-danger btn-sm cursor-pointer"
                        (click)="removePart($index)"
                        [attr.aria-label]="'Eliminar parte ' + ($index + 1)"
                      >
                        ✕
                      </button>
                    </div>
                  }
                  <button type="button" class="btn btn-secondary btn-sm mt-2 cursor-pointer" (click)="addPart()">
                    ➕ Agregar Parte Adicional
                  </button>
                </div>
              </div>
            } @else {
              <!-- MODO SERIE: CONSTRUCTOR DE TEMPORADAS Y EPISODIOS -->
              <div class="series-builder">
                <div class="builder-header">
                  <strong>Estructura de Temporadas y Capítulos</strong>
                  <button type="button" class="btn btn-primary btn-sm cursor-pointer" (click)="addSeason()">
                    ➕ Nueva Temporada
                  </button>
                </div>

                @for (season of seasons; track season.seasonNumber; let sIndex = $index) {
                  <div class="season-block">
                    <div class="season-head">
                      <h3>Temporada {{ season.seasonNumber }}</h3>
                      <div class="season-actions">
                        <button
                          type="button"
                          class="btn btn-secondary btn-sm cursor-pointer"
                          (click)="addEpisode(season)"
                          [attr.aria-label]="'Agregar episodio a temporada ' + season.seasonNumber"
                        >
                          ➕ Agregar Episodio
                        </button>
                        @if (seasons.length > 1) {
                          <button
                            type="button"
                            class="btn btn-danger btn-sm cursor-pointer"
                            (click)="removeSeason(sIndex)"
                            [attr.aria-label]="'Eliminar temporada ' + season.seasonNumber"
                          >
                            Eliminar Temporada
                          </button>
                        }
                      </div>
                    </div>

                    <div class="episodes-list">
                      @for (ep of season.episodes; track ep.episodeNumber; let eIndex = $index) {
                        <div class="episode-row">
                          <span class="ep-num" aria-hidden="true">E{{ ep.episodeNumber }}</span>
                          <label [for]="'ep-title-' + sIndex + '-' + eIndex" class="sr-only">
                            Título del Capítulo {{ ep.episodeNumber }} de Temporada {{ season.seasonNumber }}
                          </label>
                          <input
                            [id]="'ep-title-' + sIndex + '-' + eIndex"
                            type="text"
                            class="form-control ep-title"
                            placeholder="Título del Capítulo"
                            [(ngModel)]="ep.title"
                          />
                          <label [for]="'ep-url-' + sIndex + '-' + eIndex" class="sr-only">
                            URL de Audio del Capítulo {{ ep.episodeNumber }} de Temporada {{ season.seasonNumber }}
                          </label>
                          <input
                            [id]="'ep-url-' + sIndex + '-' + eIndex"
                            type="text"
                            class="form-control ep-url"
                            placeholder="URL Audio Streaming (mp3/m4a)"
                            [(ngModel)]="ep.audioUrl"
                          />
                          <label [for]="'ep-dur-' + sIndex + '-' + eIndex" class="sr-only">
                            Duración en Minutos del Capítulo {{ ep.episodeNumber }} de Temporada {{ season.seasonNumber }}
                          </label>
                          <input
                            [id]="'ep-dur-' + sIndex + '-' + eIndex"
                            type="number"
                            class="form-control ep-duration"
                            placeholder="Min"
                            [(ngModel)]="ep.durationMinutes"
                            title="Duración en minutos"
                          />
                          <button
                            type="button"
                            class="btn btn-danger btn-sm cursor-pointer"
                            (click)="removeEpisode(season, eIndex)"
                            [attr.aria-label]="'Eliminar capítulo ' + ep.episodeNumber + ' de temporada ' + season.seasonNumber"
                          >
                            ✕
                          </button>
                        </div>
                      }
                    </div>
                  </div>
                }
              </div>
            }
          </section>
        </div>

        <!-- COLUMNA LATERAL (IMÁGENES Y ESTADO) -->
        <aside class="form-sidebar" aria-label="Ajustes de publicación y previsualización">
          <!-- MATERIAL GRÁFICO & PREVIEWS -->
          <div class="card preview-card">
            <h3>🖼️ Material Gráfico</h3>

            <div class="form-group mt-3">
              <label for="content-cover-url" class="form-label">URL de Portada (Vertical)</label>
              <input
                id="content-cover-url"
                type="text"
                class="form-control"
                placeholder="https://.../cover.jpg"
                [(ngModel)]="form.coverUrl"
              />
            </div>

            <div class="cover-preview-box">
              @if (form.coverUrl) {
                <img
                  [src]="form.coverUrl"
                  [alt]="'Previsualización de portada para ' + (form.title || 'nueva obra')"
                  onerror="this.style.display='none'"
                />
              } @else {
                <div class="cover-placeholder" aria-hidden="true">
                  <span>🎬</span>
                  <small>Previsualización de Portada</small>
                </div>
              }
            </div>

            <div class="form-group mt-3">
              <label for="content-banner-url" class="form-label">URL de Banner (Horizontal)</label>
              <input
                id="content-banner-url"
                type="text"
                class="form-control"
                placeholder="https://.../banner.jpg"
                [(ngModel)]="form.bannerUrl"
              />
            </div>

            <div class="banner-preview-box">
              @if (form.bannerUrl) {
                <img
                  [src]="form.bannerUrl"
                  [alt]="'Previsualización de banner para ' + (form.title || 'nueva obra')"
                  onerror="this.style.display='none'"
                />
              } @else {
                <div class="banner-placeholder" aria-hidden="true">
                  <span>🖼️ Previsualización de Banner</span>
                </div>
              }
            </div>
          </div>

          <!-- ESTADO DE PUBLICACIÓN -->
          <div class="card status-card">
            <h3>Visibilidad & Publicación</h3>
            <div class="form-group mt-3">
              <label for="content-status" class="form-label">Estado en la Plataforma</label>
              <select id="content-status" class="form-control" [(ngModel)]="form.status">
                <option value="PUBLISHED">🟢 Publicado (Visible en App)</option>
                <option value="DRAFT">🟡 Borrador (Oculto / Privado)</option>
              </select>
            </div>
            <p class="status-hint">
              @if (form.status === 'PUBLISHED') {
                La obra estará visible de inmediato para todos los usuarios en el catálogo y recomendaciones.
              } @else {
                La obra se guardará en modo borrador y solo será accesible para administradores.
              }
            </p>
            <button
              type="button"
              class="btn btn-primary btn-block mt-3 cursor-pointer"
              (click)="saveContent()"
              [disabled]="isSaving()"
            >
              @if (isSaving()) {
                <span class="spinner" aria-hidden="true"></span>
                <span>Guardando Ficha...</span>
              } @else {
                <span>💾 Guardar Ficha Cinematográfica</span>
              }
            </button>
          </div>
        </aside>
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

    .section-title h2 {
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

    .season-head h3 {
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

    .status-hint {
      font-size: 13px;
      color: var(--text-muted);
      line-height: 1.5;
      margin-top: 10px;
      background: var(--bg-input);
      padding: 10px 12px;
      border-radius: var(--radius-sm);
      border-left: 3px solid var(--primary);
    }

    .btn-block {
      width: 100%;
    }

    .cursor-pointer {
      cursor: pointer;
    }

    .spinner {
      display: inline-block;
      width: 14px;
      height: 14px;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-radius: 50%;
      border-top-color: #fff;
      animation: spin 0.8s linear infinite;
      margin-right: 6px;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
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

  saveContent(status?: 'PUBLISHED' | 'DRAFT') {
    const finalStatus = status || this.form.status || 'PUBLISHED';
    this.form.status = finalStatus;

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
      status: finalStatus,
      categories: categories.length > 0 ? categories : undefined,
      genreRaw: this.genreInput.trim() || undefined,
      audioUrls: this.multiPartUrls.filter((u) => u.trim().length > 0),
      seasons: this.form.type === 'SERIES' ? this.seasons : undefined,
    };

    this.contentService.createContent(payload).subscribe({
      next: (created) => {
        this.isSaving.set(false);
        alert(`¡Obra "${created.title}" guardada con éxito en el catálogo!`);
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
