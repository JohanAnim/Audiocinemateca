import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfigService } from '../../core/services/config.service';
import { ContentService } from '../../core/services/content.service';
import {
  VersionConfig,
  BannerConfig,
  MaintenanceConfig,
  ChatConfig,
  DonationConfig,
  AppConfigItem,
} from '../../core/models/config.models';

@Component({
  selector: 'app-config-editor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="exec-page">
      <div class="exec-header">
        <div class="exec-title-group">
          <h1>Configuración</h1>
        </div>

        <div class="exec-header-actions">
          <button
            type="button"
            class="btn btn-secondary"
            (click)="seedDefaults()"
            [disabled]="isSaving()"
          >
            Restaurar Valores por Defecto
          </button>
          <button
            type="button"
            class="btn btn-primary"
            (click)="refreshAll()"
            [disabled]="isLoading() || isLoadingGithub()"
          >
            {{ isLoading() || isLoadingGithub() ? 'Actualizando...' : 'Actualizar' }}
          </button>
        </div>
      </div>

      @if (statusMessage()) {
        <div class="status-banner" [ngClass]="statusType()" role="status" aria-live="polite">
          <span>{{ statusMessage() }}</span>
          <button type="button" class="btn btn-secondary btn-sm" (click)="statusMessage.set(null)" aria-label="Cerrar aviso">✕</button>
        </div>
      }

      <nav class="tabs-nav" role="tablist" aria-label="Secciones de configuración">
        <button
          type="button"
          role="tab"
          id="tab-version"
          class="tab-btn"
          [class.active]="activeTab() === 'version'"
          [attr.aria-selected]="activeTab() === 'version'"
          aria-controls="tabpanel-version"
          (click)="activeTab.set('version')"
        >
          Versiones (GitHub)
        </button>

        <button
          type="button"
          role="tab"
          id="tab-banner"
          class="tab-btn"
          [class.active]="activeTab() === 'banner'"
          [attr.aria-selected]="activeTab() === 'banner'"
          aria-controls="tabpanel-banner"
          (click)="activeTab.set('banner')"
        >
          Banner
        </button>

        <button
          type="button"
          role="tab"
          id="tab-maintenance"
          class="tab-btn"
          [class.active]="activeTab() === 'maintenance'"
          [attr.aria-selected]="activeTab() === 'maintenance'"
          aria-controls="tabpanel-maintenance"
          (click)="activeTab.set('maintenance')"
        >
          Mantenimiento
        </button>

        <button
          type="button"
          role="tab"
          id="tab-chat"
          class="tab-btn"
          [class.active]="activeTab() === 'chat'"
          [attr.aria-selected]="activeTab() === 'chat'"
          aria-controls="tabpanel-chat"
          (click)="activeTab.set('chat')"
        >
          Chat
        </button>

        <button
          type="button"
          role="tab"
          id="tab-donations"
          class="tab-btn"
          [class.active]="activeTab() === 'donations'"
          [attr.aria-selected]="activeTab() === 'donations'"
          aria-controls="tabpanel-donations"
          (click)="activeTab.set('donations')"
        >
          Donaciones
        </button>

        <button
          type="button"
          role="tab"
          id="tab-raw"
          class="tab-btn"
          [class.active]="activeTab() === 'raw'"
          [attr.aria-selected]="activeTab() === 'raw'"
          aria-controls="tabpanel-raw"
          (click)="activeTab.set('raw')"
        >
          Base de Datos
        </button>
      </nav>

      <!-- 1. PESTAÑA: VERSIONES EN GITHUB -->
      @if (activeTab() === 'version') {
        <section
          id="tabpanel-version"
          role="tabpanel"
          tabindex="0"
          aria-labelledby="tab-version"
          class="exec-page"
        >
          <div class="card release-hero-card">
            <div class="section-header-row">
              <div>
                <h2>Versiones en GitHub Releases</h2>
                <p>
                  Repositorio:
                  <a [href]="githubData()?.repositoryUrl || 'https://github.com/JohanAnim/Audiocinemateca'" target="_blank" rel="noopener" class="repo-link">
                    {{ githubData()?.repository || 'JohanAnim/Audiocinemateca' }}
                  </a>
                </p>
              </div>
              <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap;">
                <button
                  type="button"
                  class="btn btn-secondary btn-sm"
                  (click)="loadGithubReleases(true)"
                  [disabled]="isLoadingGithub()"
                >
                  {{ isLoadingGithub() ? 'Consultando...' : 'Sincronizar con GitHub' }}
                </button>
              </div>
            </div>

            @if (isLoadingGithub() && !githubData()) {
              <div style="padding: 32px; text-align: center; color: var(--text-muted);" role="status" aria-live="polite">
                Consultando GitHub Releases...
              </div>
            } @else if (githubData()?.latestRelease; as latest) {
              <div class="grid-4" style="margin-bottom: 20px;">
                <div class="kpi-mini-card">
                  <span class="kpi-label">Última Versión</span>
                  <strong class="kpi-val" style="color: #818cf8;">{{ latest.tagName }}</strong>
                  <span class="kpi-sub">{{ latest.name }}</span>
                </div>

                <div class="kpi-mini-card">
                  <span class="kpi-label">Descargas (Última Versión)</span>
                  <strong class="kpi-val" style="color: #34d399;">
                    {{ latest.apkAsset?.downloadCount || 0 | number }}
                  </strong>
                  <span class="kpi-sub">{{ latest.apkAsset?.name || 'APK' }} ({{ latest.apkAsset?.sizeMb || 'N/A' }})</span>
                </div>

                <div class="kpi-mini-card">
                  <span class="kpi-label">Descargas Totales</span>
                  <strong class="kpi-val" style="color: #22d3ee;">
                    {{ githubData()?.totalDownloadsAllTime || 0 | number }}
                  </strong>
                  <span class="kpi-sub">{{ githubData()?.totalReleases || 1 }} versiones publicadas</span>
                </div>

                <div class="kpi-mini-card">
                  <span class="kpi-label">Fecha</span>
                  <strong class="kpi-val" style="font-size: 18px;">
                    {{ latest.publishedAt | date:'mediumDate' }}
                  </strong>
                  <span class="kpi-sub">{{ latest.author || 'JohanAnim' }}</span>
                </div>
              </div>

              <div class="apk-actions-bar">
                @if (latest.apkAsset?.browserDownloadUrl) {
                  <a
                    [href]="latest.apkAsset.browserDownloadUrl"
                    target="_blank"
                    rel="noopener"
                    class="btn btn-primary"
                  >
                    Descargar APK ({{ latest.apkAsset.sizeMb }})
                  </a>

                  <button
                    type="button"
                    class="btn btn-secondary"
                    (click)="copyText(latest.apkAsset.browserDownloadUrl, 'Enlace copiado')"
                  >
                    Copiar Enlace APK
                  </button>
                }

                <a
                  [href]="latest.htmlUrl"
                  target="_blank"
                  rel="noopener"
                  class="btn btn-secondary"
                >
                  Abrir en GitHub
                </a>

                <button
                  type="button"
                  class="btn btn-success"
                  (click)="notifyNewReleasePush(latest)"
                  [disabled]="isSaving()"
                >
                  Notificar Versión por Push
                </button>
              </div>

              <div class="changelog-panel">
                <div class="changelog-header">
                  <strong>Notas de la Versión ({{ latest.tagName }})</strong>
                </div>
                <pre class="changelog-body">{{ latest.changelog }}</pre>
              </div>
            } @else {
              <div style="padding: 20px; text-align: center; color: var(--text-muted);">
                No se encontraron versiones disponibles.
              </div>
            }
          </div>

          @if (githubData()?.releases?.length > 1) {
            <div class="card">
              <div class="section-header-row">
                <div>
                  <h3>Historial de Versiones</h3>
                </div>
              </div>

              <div class="table-responsive">
                <table class="table">
                  <caption class="sr-only">Historial de versiones en GitHub Releases</caption>
                  <thead>
                    <tr>
                      <th scope="col">Versión</th>
                      <th scope="col">Nombre</th>
                      <th scope="col">Fecha</th>
                      <th scope="col">Archivo</th>
                      <th scope="col">Tamaño</th>
                      <th scope="col">Descargas</th>
                      <th scope="col">Acciones</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (rel of githubData()?.releases; track rel.id; let idx = $index) {
                      <tr>
                        <td>
                          <span class="badge" [ngClass]="idx === 0 ? 'badge-success' : 'badge-primary'">
                            {{ rel.tagName }}
                          </span>
                        </td>
                        <td><strong>{{ rel.name }}</strong></td>
                        <td>{{ rel.publishedAt | date:'mediumDate' }}</td>
                        <td><code>{{ rel.apkAsset?.name || '—' }}</code></td>
                        <td>{{ rel.apkAsset?.sizeMb || '—' }}</td>
                        <td><strong>{{ rel.apkAsset?.downloadCount || 0 | number }}</strong></td>
                        <td>
                          <div style="display: flex; gap: 6px;">
                            @if (rel.apkAsset?.browserDownloadUrl) {
                              <a
                                [href]="rel.apkAsset.browserDownloadUrl"
                                target="_blank"
                                rel="noopener"
                                class="btn btn-secondary btn-sm"
                              >
                                Descargar
                              </a>
                            }
                            <a
                              [href]="rel.htmlUrl"
                              target="_blank"
                              rel="noopener"
                              class="btn btn-secondary btn-sm"
                            >
                              GitHub
                            </a>
                          </div>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </div>
          }

          <div class="card">
            <div class="section-header-row">
              <div>
                <h3>Versión Mínima Requerida</h3>
              </div>
            </div>

            <div class="grid-2">
              <div class="form-group">
                <label for="cfg-min-version" class="form-label">Versión Mínima Permitida</label>
                <input
                  id="cfg-min-version"
                  type="text"
                  class="form-control"
                  [(ngModel)]="versionConfig.minRequiredVersion"
                  placeholder="3.0.0"
                />
              </div>

              <div class="form-group" style="display: flex; align-items: center; padding-top: 18px;">
                <label for="cfg-force-update" class="toggle-box" style="width: 100%;">
                  <input
                    id="cfg-force-update"
                    type="checkbox"
                    [(ngModel)]="versionConfig.forceUpdate"
                  />
                  <span>Exigir actualización obligatoria</span>
                </label>
              </div>
            </div>

            <div class="card-footer-actions">
              <span></span>
              <button
                type="button"
                class="btn btn-primary"
                (click)="saveVersionConfig()"
                [disabled]="isSaving()"
              >
                Guardar
              </button>
            </div>
          </div>
        </section>
      }

      <!-- 2. PESTAÑA: BANNER -->
      @if (activeTab() === 'banner') {
        <section
          id="tabpanel-banner"
          role="tabpanel"
          tabindex="0"
          aria-labelledby="tab-banner"
          class="card"
        >
          <div class="section-header-row">
            <div>
              <h2>Banner Principal</h2>
            </div>
            <label for="cfg-banner-enabled" class="toggle-box">
              <input id="cfg-banner-enabled" type="checkbox" [(ngModel)]="bannerConfig.enabled" />
              <strong>{{ bannerConfig.enabled ? 'Visible' : 'Oculto' }}</strong>
            </label>
          </div>

          <div class="smart-lookup-box">
            <label for="cfg-banner-action-url" class="form-label">
              Enlace o ID del Contenido
            </label>
            <div style="display: flex; gap: 10px; flex-wrap: wrap;">
              <input
                id="cfg-banner-action-url"
                type="text"
                class="form-control"
                style="flex: 1; min-width: 260px;"
                [(ngModel)]="bannerConfig.actionUrl"
                placeholder="https://audiocinemateca.com/peliculas?id=150"
              />
              <button
                type="button"
                class="btn btn-secondary"
                (click)="lookupBannerWork()"
                [disabled]="isLookingUpWork()"
              >
                {{ isLookingUpWork() ? 'Buscando...' : 'Autocompletar desde Catálogo' }}
              </button>
            </div>
          </div>

          <div class="grid-2" style="margin-top: 16px;">
            <div class="form-group">
              <label for="cfg-banner-title" class="form-label">Título *</label>
              <input
                id="cfg-banner-title"
                type="text"
                class="form-control"
                [(ngModel)]="bannerConfig.title"
                placeholder="Título del banner"
              />
            </div>

            <div class="form-group">
              <label for="cfg-banner-type" class="form-label">Tipo</label>
              <select id="cfg-banner-type" class="form-control" [(ngModel)]="bannerConfig.type">
                <option value="info">Información</option>
                <option value="update">Actualización</option>
                <option value="warning">Aviso</option>
                <option value="alert">Alerta</option>
              </select>
            </div>
          </div>

          <div class="form-group">
            <label for="cfg-banner-message" class="form-label">Descripción *</label>
            <textarea
              id="cfg-banner-message"
              class="form-control"
              rows="3"
              [(ngModel)]="bannerConfig.message"
              placeholder="Texto del banner..."
            ></textarea>
          </div>

          <div class="grid-2">
            <div class="form-group">
              <label for="cfg-banner-action-text" class="form-label">Texto del Botón</label>
              <input
                id="cfg-banner-action-text"
                type="text"
                class="form-control"
                [(ngModel)]="bannerConfig.actionText"
                placeholder="Escuchar ahora"
              />
            </div>

            <div class="form-group">
              <label for="cfg-banner-id" class="form-label">Identificador</label>
              <input
                id="cfg-banner-id"
                type="text"
                class="form-control"
                [(ngModel)]="bannerConfig.id"
                placeholder="banner_1"
              />
            </div>
          </div>

          <div class="push-banner-options">
            <label for="cfg-banner-dismissible" class="toggle-box">
              <input id="cfg-banner-dismissible" type="checkbox" [(ngModel)]="bannerConfig.dismissible" />
              <span>Permitir cerrar el banner</span>
            </label>

            <label for="cfg-banner-send-push" class="toggle-box">
              <input id="cfg-banner-send-push" type="checkbox" [(ngModel)]="bannerSendPush" />
              <span>Enviar notificación Push al publicar</span>
            </label>

            @if (bannerSendPush) {
              <label for="cfg-banner-ai-push" class="toggle-box">
                <input id="cfg-banner-ai-push" type="checkbox" [(ngModel)]="bannerOptimizePushWithAi" />
                <span>Redactar texto Push con IA</span>
              </label>
            }
          </div>

          <div class="card-footer-actions">
            <span></span>
            <button
              type="button"
              class="btn btn-primary"
              (click)="saveBannerConfig()"
              [disabled]="isSaving()"
            >
              {{ isSaving() ? 'Guardando...' : 'Guardar Banner' }}
            </button>
          </div>
        </section>
      }

      <!-- 3. PESTAÑA: MODO MANTENIMIENTO -->
      @if (activeTab() === 'maintenance') {
        <section
          id="tabpanel-maintenance"
          role="tabpanel"
          tabindex="0"
          aria-labelledby="tab-maintenance"
          class="card"
        >
          <div class="section-header-row">
            <div>
              <h2>Modo Mantenimiento</h2>
            </div>
            <label for="cfg-maint-enabled" class="toggle-box">
              <input id="cfg-maint-enabled" type="checkbox" [(ngModel)]="maintenanceConfig.enabled" />
              <strong>
                {{ maintenanceConfig.enabled ? 'Mantenimiento Activo' : 'Desactivado' }}
              </strong>
            </label>
          </div>

          <div class="form-group">
            <label for="cfg-maint-message" class="form-label">Mensaje para los usuarios</label>
            <textarea
              id="cfg-maint-message"
              class="form-control"
              rows="3"
              [(ngModel)]="maintenanceConfig.message"
            ></textarea>
          </div>

          <div class="form-group">
            <label for="cfg-maint-estimated-end" class="form-label">Tiempo estimado de finalización</label>
            <input
              id="cfg-maint-estimated-end"
              type="text"
              class="form-control"
              [(ngModel)]="maintenanceConfig.estimatedEndTime"
              placeholder="Ej. 18:00"
            />
          </div>

          <div class="card-footer-actions">
            <span></span>
            <button
              type="button"
              class="btn btn-primary"
              (click)="saveMaintenanceConfig()"
              [disabled]="isSaving()"
            >
              Guardar
            </button>
          </div>
        </section>
      }

      <!-- 4. PESTAÑA: CHAT -->
      @if (activeTab() === 'chat') {
        <section
          id="tabpanel-chat"
          role="tabpanel"
          tabindex="0"
          aria-labelledby="tab-chat"
          class="card"
        >
          <div class="section-header-row">
            <div>
              <h2>Configuración del Chat</h2>
            </div>
            <label for="cfg-chat-enabled" class="toggle-box">
              <input id="cfg-chat-enabled" type="checkbox" [(ngModel)]="chatConfig.enabled" />
              <strong>{{ chatConfig.enabled ? 'Habilitado' : 'Deshabilitado' }}</strong>
            </label>
          </div>

          <div class="grid-2">
            <div class="form-group">
              <label for="cfg-chat-slowmode" class="form-label">Espera entre mensajes (segundos)</label>
              <input
                id="cfg-chat-slowmode"
                type="number"
                class="form-control"
                [(ngModel)]="chatConfig.slowModeSeconds"
                min="0"
                max="60"
              />
            </div>

            <div class="form-group">
              <label for="cfg-chat-max-len" class="form-label">Longitud máxima por mensaje</label>
              <input
                id="cfg-chat-max-len"
                type="number"
                class="form-control"
                [(ngModel)]="chatConfig.maxMessageLength"
                min="50"
                max="2000"
              />
            </div>
          </div>

          <div class="form-group">
            <label for="cfg-chat-welcome" class="form-label">Mensaje de bienvenida</label>
            <textarea
              id="cfg-chat-welcome"
              class="form-control"
              rows="3"
              [(ngModel)]="chatConfig.welcomeMessage"
            ></textarea>
          </div>

          <div class="card-footer-actions">
            <span></span>
            <button
              type="button"
              class="btn btn-primary"
              (click)="saveChatConfig()"
              [disabled]="isSaving()"
            >
              Guardar
            </button>
          </div>
        </section>
      }

      <!-- 5. PESTAÑA: DONACIONES -->
      @if (activeTab() === 'donations') {
        <section
          id="tabpanel-donations"
          role="tabpanel"
          tabindex="0"
          aria-labelledby="tab-donations"
          class="card"
        >
          <div class="section-header-row">
            <div>
              <h2>Donaciones y Soporte</h2>
            </div>
          </div>

          <div class="grid-2">
            <div class="form-group">
              <label for="cfg-donations-paypal" class="form-label">Enlace PayPal</label>
              <input
                id="cfg-donations-paypal"
                type="url"
                class="form-control"
                [(ngModel)]="donationConfig.paypal"
              />
            </div>

            <div class="form-group">
              <label for="cfg-donations-paypalme" class="form-label">Enlace PayPal.Me</label>
              <input
                id="cfg-donations-paypalme"
                type="url"
                class="form-control"
                [(ngModel)]="donationConfig.paypalMe"
              />
            </div>
          </div>

          <div class="form-group">
            <label for="cfg-donations-email" class="form-label">Correo de Soporte</label>
            <input
              id="cfg-donations-email"
              type="email"
              class="form-control"
              [(ngModel)]="donationConfig.supportEmail"
            />
          </div>

          <div class="card-footer-actions">
            <span></span>
            <button
              type="button"
              class="btn btn-primary"
              (click)="saveDonationsConfig()"
              [disabled]="isSaving()"
            >
              Guardar
            </button>
          </div>
        </section>
      }

      <!-- 6. PESTAÑA: BASE DE DATOS -->
      @if (activeTab() === 'raw') {
        <section
          id="tabpanel-raw"
          role="tabpanel"
          tabindex="0"
          aria-labelledby="tab-raw"
          class="card"
        >
          <div class="section-header-row">
            <div>
              <h2>Registros en <code>app_configs</code></h2>
            </div>
          </div>

          <div style="display: flex; flex-direction: column; gap: 12px;">
            @for (item of rawConfigs(); track item.key) {
              <div class="raw-config-item">
                <div class="raw-config-head">
                  <div>
                    <strong style="color: #818cf8; font-family: monospace; font-size: 14px;">{{ item.key }}</strong>
                    @if (item.description) {
                      <span style="color: var(--text-muted); font-size: 12px; margin-left: 8px;">• {{ item.description }}</span>
                    }
                  </div>
                  <small style="color: var(--text-dim); font-family: monospace;">{{ item.updatedAt | date:'short' }}</small>
                </div>
                <pre class="raw-config-pre">{{ item.value | json }}</pre>
              </div>
            }
          </div>
        </section>
      }
    </div>
  `,
  styles: [`
    .repo-link {
      color: #818cf8;
      font-weight: 700;
      text-decoration: underline;
    }

    .apk-actions-bar {
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
      padding: 16px;
      background-color: var(--bg-input);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-sm);
      margin-bottom: 20px;
    }

    .changelog-panel {
      background-color: var(--bg-input);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-sm);
      overflow: hidden;
    }

    .changelog-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;
      padding: 12px 16px;
      background-color: rgba(255, 255, 255, 0.03);
      border-bottom: 1px solid var(--border-color);
      font-size: 13px;
    }

    .changelog-header small {
      color: var(--text-dim);
    }

    .changelog-body {
      padding: 16px;
      font-family: inherit;
      font-size: 13.5px;
      line-height: 1.65;
      color: var(--text-main);
      white-space: pre-wrap;
      max-height: 340px;
      overflow-y: auto;
    }

    .banner-preview-card {
      padding: 20px;
      border-radius: var(--radius-sm);
      border: 1px solid rgba(99, 102, 241, 0.35);
      background: linear-gradient(135deg, rgba(99, 102, 241, 0.14), rgba(15, 23, 42, 0.9));
      margin-bottom: 20px;
    }

    .banner-preview-card.preview-warning {
      border-color: rgba(245, 158, 11, 0.4);
      background: linear-gradient(135deg, rgba(245, 158, 11, 0.14), rgba(15, 23, 42, 0.9));
    }

    .banner-preview-card.preview-alert {
      border-color: rgba(239, 68, 68, 0.4);
      background: linear-gradient(135deg, rgba(239, 68, 68, 0.14), rgba(15, 23, 42, 0.9));
    }

    .banner-preview-card.preview-update {
      border-color: rgba(16, 185, 129, 0.4);
      background: linear-gradient(135deg, rgba(16, 185, 129, 0.14), rgba(15, 23, 42, 0.9));
    }

    .preview-meta {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      margin-bottom: 10px;
    }

    .banner-preview-card h3 {
      font-size: 18px;
      font-weight: 800;
      margin-bottom: 6px;
    }

    .banner-preview-card p {
      font-size: 14px;
      color: var(--text-muted);
    }

    .smart-lookup-box {
      padding: 16px;
      background-color: var(--bg-input);
      border: 1px solid rgba(99, 102, 241, 0.25);
      border-radius: var(--radius-sm);
    }

    .push-banner-options {
      display: flex;
      gap: 14px;
      flex-wrap: wrap;
      margin-top: 12px;
    }

    .raw-config-item {
      background-color: var(--bg-input);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-sm);
      padding: 14px 16px;
    }

    .raw-config-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 10px;
    }

    .raw-config-pre {
      background-color: rgba(0, 0, 0, 0.35);
      padding: 12px;
      border-radius: 6px;
      font-size: 12px;
      color: #cbd5e1;
      overflow-x: auto;
      max-height: 220px;
    }
  `],
})
export class ConfigEditorComponent implements OnInit {
  private readonly configService = inject(ConfigService);
  private readonly contentService = inject(ContentService);

  readonly isLoading = signal(false);
  readonly isLoadingGithub = signal(false);
  readonly isSaving = signal(false);
  readonly isLookingUpWork = signal(false);

  readonly activeTab = signal<'version' | 'banner' | 'maintenance' | 'chat' | 'donations' | 'raw'>('version');
  readonly statusMessage = signal<string | null>(null);
  readonly statusType = signal<'success' | 'error' | 'info'>('success');
  readonly rawConfigs = signal<AppConfigItem[]>([]);
  readonly githubData = signal<any | null>(null);
  readonly bannerMatchedWork = signal<any | null>(null);

  bannerSendPush = false;
  bannerOptimizePushWithAi = true;

  versionConfig: VersionConfig = {
    version: '3.1.0',
    versionCode: 31,
    name: 'Audiocinemateca v3.1.0',
    changelog: '',
    downloadUrl: '',
    minRequiredVersion: '3.0.0',
    forceUpdate: false,
    updatedAt: new Date().toISOString(),
    downloadCount: 0,
  };

  bannerConfig: BannerConfig = {
    enabled: true,
    id: 'welcome_v3',
    title: '¡Bienvenido a Audiocinemateca!',
    message: '',
    type: 'info',
    dismissible: true,
    actionUrl: null,
    actionText: 'Escuchar ahora',
  };

  maintenanceConfig: MaintenanceConfig = {
    enabled: false,
    message: 'Audiocinemateca se encuentra en mantenimiento programado. Volveremos muy pronto.',
    estimatedEndTime: null,
  };

  chatConfig: ChatConfig = {
    enabled: true,
    slowModeSeconds: 3,
    maxMessageLength: 500,
    welcomeMessage: '',
  };

  donationConfig: DonationConfig = {
    paypal: '',
    paypalMe: '',
    supportEmail: '',
  };

  ngOnInit() {
    this.refreshAll();
  }

  refreshAll() {
    this.loadConfigs();
    this.loadGithubReleases(false);
  }

  loadGithubReleases(forceRefresh = false) {
    this.isLoadingGithub.set(true);
    this.configService.getGithubReleases(forceRefresh).subscribe({
      next: (data) => {
        this.githubData.set(data);
        this.isLoadingGithub.set(false);
        if (forceRefresh) {
          this.showStatus('Datos de GitHub Releases sincronizados en tiempo real', 'success');
        }
      },
      error: () => {
        this.isLoadingGithub.set(false);
      },
    });
  }

  loadConfigs() {
    this.isLoading.set(true);
    this.configService.getAllConfigs().subscribe({
      next: (items) => {
        this.rawConfigs.set(items);
        for (const item of items) {
          if (item.key === 'app_version') {
            this.versionConfig = { ...this.versionConfig, ...item.value };
          } else if (item.key === 'featured_banner') {
            this.bannerConfig = { ...this.bannerConfig, ...item.value };
          } else if (item.key === 'maintenance_mode') {
            this.maintenanceConfig = { ...this.maintenanceConfig, ...item.value };
          } else if (item.key === 'chat_config') {
            this.chatConfig = { ...this.chatConfig, ...item.value };
          } else if (item.key === 'donation_links') {
            this.donationConfig = { ...this.donationConfig, ...item.value };
          }
        }
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
        this.showStatus('Error al cargar configuraciones remotas', 'error');
      },
    });
  }

  copyText(text: string, msg: string) {
    if (typeof navigator !== 'undefined' && text) {
      navigator.clipboard.writeText(text);
      this.showStatus(msg, 'success');
    }
  }

  notifyNewReleasePush(latest: any) {
    if (!latest) return;
    if (!confirm(`¿Deseas enviar una notificación Push global anunciando la versión ${latest.tagName} (${latest.name})?`)) {
      return;
    }
    this.isSaving.set(true);
    this.configService
      .sendPushNotification({
        title: `📲 Nueva actualización disponible: ${latest.name || latest.tagName}`,
        body: `Ya puedes actualizar Audiocinemateca a la versión ${latest.tagName} desde la pestaña Cuenta.`,
        destination: 'app',
      })
      .subscribe({
        next: (res) => {
          this.isSaving.set(false);
          this.showStatus(res?.message || 'Notificación de nueva versión enviada con éxito', 'success');
        },
        error: () => {
          this.isSaving.set(false);
          this.showStatus('Error al enviar notificación push de versión', 'error');
        },
      });
  }

  lookupBannerWork() {
    const query = (this.bannerConfig.actionUrl || '').trim();
    if (!query) {
      this.showStatus('Escribe una URL o ID del catálogo para buscar la obra', 'info');
      return;
    }
    this.isLookingUpWork.set(true);
    this.contentService.lookupCatalogItem(query).subscribe({
      next: (res) => {
        this.isLookingUpWork.set(false);
        if (res?.found && res.item) {
          this.bannerMatchedWork.set(res.item);
          if (!this.bannerConfig.title || this.bannerConfig.title === '¡Bienvenido a Audiocinemateca!') {
            this.bannerConfig.title = `🎬 Destacado: ${res.item.title}`;
          }
          if (!this.bannerConfig.message && res.item.synopsis) {
            this.bannerConfig.message = res.item.synopsis.substring(0, 220);
          }
          this.showStatus(
            `Obra detectada: "${res.item.title}" (${res.item.itemType.toUpperCase()} • Géneros: ${(res.item.genres || []).join(', ')})`,
            'success',
          );
        } else {
          this.bannerMatchedWork.set(null);
          this.showStatus('No se encontró coincidencia exacta en el catálogo maestro; se usará como enlace general.', 'info');
        }
      },
      error: () => {
        this.isLookingUpWork.set(false);
        this.showStatus('Error al consultar el catálogo maestro', 'error');
      },
    });
  }

  saveVersionConfig() {
    this.isSaving.set(true);
    this.versionConfig.updatedAt = new Date().toISOString();
    this.configService
      .updateConfig('app_version', this.versionConfig, 'Política de versión y compatibilidad')
      .subscribe({
        next: () => {
          this.isSaving.set(false);
          this.showStatus('Política de versión guardada correctamente', 'success');
          this.loadConfigs();
        },
        error: () => {
          this.isSaving.set(false);
          this.showStatus('Error al guardar política de versión', 'error');
        },
      });
  }

  saveBannerConfig() {
    if (!this.bannerConfig.title?.trim() || !this.bannerConfig.message?.trim()) {
      this.showStatus('El título y la descripción del banner son obligatorios', 'error');
      return;
    }
    this.isSaving.set(true);
    this.configService
      .publishBanner({
        ...this.bannerConfig,
        sendPush: this.bannerSendPush,
        optimizePushWithAi: this.bannerOptimizePushWithAi,
      })
      .subscribe({
        next: (res) => {
          this.isSaving.set(false);
          const extra = res?.matchedCatalogWork ? ` (Vinculado a "${res.matchedCatalogWork}")` : '';
          this.showStatus(`Banner global publicado exitosamente${extra}`, 'success');
          this.loadConfigs();
        },
        error: () => {
          this.isSaving.set(false);
          this.showStatus('Error al publicar el banner global', 'error');
        },
      });
  }

  saveMaintenanceConfig() {
    this.isSaving.set(true);
    this.configService
      .updateConfig('maintenance_mode', this.maintenanceConfig, 'Estado global de modo mantenimiento')
      .subscribe({
        next: () => {
          this.isSaving.set(false);
          this.showStatus(
            this.maintenanceConfig.enabled
              ? 'Modo Mantenimiento ACTIVADO en toda la plataforma'
              : 'Sistema puesto en modo operativo normal',
            'success',
          );
          this.loadConfigs();
        },
        error: () => {
          this.isSaving.set(false);
          this.showStatus('Error al actualizar el modo mantenimiento', 'error');
        },
      });
  }

  saveChatConfig() {
    this.isSaving.set(true);
    this.configService
      .updateConfig('chat_config', this.chatConfig, 'Parámetros de operación y límites del chat global')
      .subscribe({
        next: () => {
          this.isSaving.set(false);
          this.showStatus('Configuración del chat guardada con éxito', 'success');
          this.loadConfigs();
        },
        error: () => {
          this.isSaving.set(false);
          this.showStatus('Error al guardar la configuración del chat', 'error');
        },
      });
  }

  saveDonationsConfig() {
    this.isSaving.set(true);
    this.configService
      .updateConfig('donation_links', this.donationConfig, 'Enlaces oficiales de donación y soporte')
      .subscribe({
        next: () => {
          this.isSaving.set(false);
          this.showStatus('Canales de donación actualizados correctamente', 'success');
          this.loadConfigs();
        },
        error: () => {
          this.isSaving.set(false);
          this.showStatus('Error al actualizar canales de donación', 'error');
        },
      });
  }

  seedDefaults() {
    this.isSaving.set(true);
    this.configService.seedDefaults().subscribe({
      next: () => {
        this.isSaving.set(false);
        this.showStatus('Valores por defecto verificados y restaurados', 'success');
        this.loadConfigs();
      },
      error: () => {
        this.isSaving.set(false);
        this.showStatus('Error al restaurar valores por defecto', 'error');
      },
    });
  }

  private showStatus(msg: string, type: 'success' | 'error' | 'info') {
    this.statusMessage.set(msg);
    this.statusType.set(type);
    setTimeout(() => {
      this.statusMessage.set(null);
    }, 5000);
  }
}
