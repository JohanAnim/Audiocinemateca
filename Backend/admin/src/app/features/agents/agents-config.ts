import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfigService } from '../../core/services/config.service';
import { ContentService } from '../../core/services/content.service';

export type AiProviderType = 'groq' | 'gemini' | 'custom';
export type ReasoningEffortType = 'low' | 'medium' | 'high';

export interface AiModelDefinition {
  id: string;
  name: string;
  badge: string;
  description: string;
  supportsThinking: boolean;
  defaultThinking: boolean;
}

export interface AiProviderCatalogEntry {
  id: AiProviderType;
  order: number;
  name: string;
  subtitle: string;
  badge: string;
  baseUrl: string;
  docsUrl: string;
  keyPlaceholder: string;
  defaultModel: string;
  models: AiModelDefinition[];
}

export interface AuraAgentConfig {
  enabled: boolean;
  provider: AiProviderType;
  model: string;
  thinkingEnabled: boolean;
  reasoningEffort: ReasoningEffortType;
  temperature: number;
  maxTokens: number;
  customBaseUrl: string;
  customModelForceThinking: boolean;
  systemPrompt: string;
  enableCatalogSearch: boolean;
  enableCommunityChatBot: boolean;
  chatMentionKeyword: string;
}

export interface ProviderKeyStatus {
  hasKey: boolean;
  maskedKey: string | null;
  source: 'database' | 'env' | 'none';
}

export interface CronTasksConfig {
  cleanOldChatDays: number;
  cleanChatCount: number;
  autoCleanEnabled: boolean;
  catalogLinkAuditIntervalHours: number;
  recalculateRankingsDaily: boolean;
}

export interface PushNotificationForm {
  title: string;
  body: string;
  target: 'all' | 'android' | 'desktop';
  destination: 'app' | 'content_detail' | 'announcements' | 'global_chat' | 'external_url' | 'donation';
  contentId: string;
  contentType: string;
  url: string;
  delayMinutes: number;
}

@Component({
  selector: 'app-agents-config',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="exec-page">
      <!-- ENCABEZADO -->
      <div class="exec-header">
        <div class="exec-title-group">
          <h1>Aura IA y Automatizaciones</h1>
          <p>Configuración del motor de IA, notificaciones Push, colecciones y mantenimiento</p>
        </div>

        <div class="exec-header-actions">
          <button
            type="button"
            class="btn btn-secondary"
            (click)="loadAll()"
            [disabled]="isLoading()"
          >
            {{ isLoading() ? 'Actualizando...' : 'Actualizar' }}
          </button>
        </div>
      </div>

      @if (statusMessage()) {
        <div class="status-banner" [ngClass]="statusType()" role="status" aria-live="polite">
          <span>{{ statusMessage() }}</span>
          <button type="button" class="btn btn-secondary btn-sm" (click)="statusMessage.set(null)" aria-label="Cerrar aviso">✕</button>
        </div>
      }

      <!-- PESTAÑAS -->
      <nav class="tabs-nav" role="tablist" aria-label="Secciones de IA y automatizaciones">
        <button
          type="button"
          role="tab"
          id="tab-aura"
          class="tab-btn"
          [class.active]="activeTab() === 'aura'"
          [attr.aria-selected]="activeTab() === 'aura'"
          aria-controls="tabpanel-aura"
          (click)="activeTab.set('aura')"
        >
          Configuración de Aura
        </button>

        <button
          type="button"
          role="tab"
          id="tab-push"
          class="tab-btn"
          [class.active]="activeTab() === 'push'"
          [attr.aria-selected]="activeTab() === 'push'"
          aria-controls="tabpanel-push"
          (click)="activeTab.set('push')"
        >
          Notificaciones Push
        </button>

        <button
          type="button"
          role="tab"
          id="tab-collections"
          class="tab-btn"
          [class.active]="activeTab() === 'collections'"
          [attr.aria-selected]="activeTab() === 'collections'"
          aria-controls="tabpanel-collections"
          (click)="activeTab.set('collections')"
        >
          Colecciones y Ranking
        </button>

        <button
          type="button"
          role="tab"
          id="tab-cron"
          class="tab-btn"
          [class.active]="activeTab() === 'cron'"
          [attr.aria-selected]="activeTab() === 'cron'"
          aria-controls="tabpanel-cron"
          (click)="activeTab.set('cron')"
        >
          Mantenimiento
        </button>
      </nav>

      <!-- =====================================================================
           1. PESTAÑA: CONFIGURACIÓN DE AURA
           ===================================================================== -->
      @if (activeTab() === 'aura') {
        <div
          id="tabpanel-aura"
          role="tabpanel"
          tabindex="0"
          aria-labelledby="tab-aura"
          class="exec-page"
        >
          <!-- PROVEEDOR, MODELO Y CLAVE API -->
          <section class="card" aria-labelledby="heading-brain-config">
            <div class="section-header-row">
              <div>
                <h2 id="heading-brain-config">Proveedor y Modelo</h2>
              </div>
              <div style="display: flex; gap: 10px; align-items: center;">
                <button
                  type="button"
                  class="btn btn-secondary btn-sm"
                  (click)="syncLiveModelsForCurrentProvider()"
                  [disabled]="isSyncingModels()"
                >
                  {{ isSyncingModels() ? 'Sincronizando...' : 'Sincronizar modelos desde API' }}
                </button>
                <label for="aura-enabled" class="toggle-box" style="padding: 8px 14px;">
                  <input id="aura-enabled" type="checkbox" [(ngModel)]="auraConfig.enabled" />
                  <strong>{{ auraConfig.enabled ? 'Aura Activa' : 'Aura Inactiva' }}</strong>
                </label>
              </div>
            </div>

            <div class="grid-2">
              <!-- LISTA DESPLEGABLE 1: PROVEEDOR DE IA -->
              <div class="form-group">
                <label for="aura-provider-select" class="form-label">Proveedor de IA</label>
                <select
                  id="aura-provider-select"
                  class="form-control"
                  [(ngModel)]="auraConfig.provider"
                  (ngModelChange)="selectProvider($event)"
                >
                  @for (prov of providerCatalog(); track prov.id) {
                    <option [value]="prov.id">{{ prov.name }}</option>
                  }
                </select>
              </div>

              <!-- LISTA DESPLEGABLE 2: MODELO -->
              <div class="form-group">
                <label for="aura-model-select" class="form-label">Modelo</label>
                <select
                  id="aura-model-select"
                  class="form-control"
                  [(ngModel)]="auraConfig.model"
                  (ngModelChange)="onModelSelectChange($event)"
                >
                  @for (m of getCurrentProviderEntry().models; track m.id) {
                    <option [value]="m.id">{{ m.name }}</option>
                  }
                </select>
              </div>
            </div>

            @if (auraConfig.provider === 'custom') {
              <div class="grid-2">
                <div class="form-group">
                  <label for="aura-custom-base-url" class="form-label">URL Base del Endpoint (OpenAI Compatible)</label>
                  <input
                    id="aura-custom-base-url"
                    type="url"
                    class="form-control"
                    [(ngModel)]="auraConfig.customBaseUrl"
                    placeholder="https://api.openai.com/v1"
                  />
                </div>

                <div class="form-group">
                  <label for="aura-custom-model-input" class="form-label">ID de Modelo Personalizado</label>
                  <input
                    id="aura-custom-model-input"
                    type="text"
                    class="form-control"
                    [(ngModel)]="auraConfig.model"
                    placeholder="Ej. gpt-4o-mini, deepseek-chat"
                  />
                </div>
              </div>
            }

            <!-- CLAVE API Y PENSAMIENTO PROFUNDO -->
            <div class="grid-2">
              <div class="form-group">
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <label for="aura-api-key-input" class="form-label">
                    Clave API ({{ getCurrentProviderEntry().name }})
                  </label>
                  @if (getKeyStatus(auraConfig.provider).hasKey) {
                    <span class="badge badge-success">
                      Guardada: {{ getKeyStatus(auraConfig.provider).maskedKey }}
                    </span>
                  } @else {
                    <span class="badge badge-warning">Sin clave configurada</span>
                  }
                </div>

                <div style="display: flex; gap: 8px;">
                  <input
                    id="aura-api-key-input"
                    [type]="showApiKey() ? 'text' : 'password'"
                    class="form-control"
                    [(ngModel)]="apiKeyInputs[auraConfig.provider]"
                    [placeholder]="getKeyStatus(auraConfig.provider).hasKey ? 'Dejar en blanco para mantener la clave actual' : getCurrentProviderEntry().keyPlaceholder"
                    autocomplete="off"
                  />
                  <button
                    type="button"
                    class="btn btn-secondary"
                    (click)="showApiKey.set(!showApiKey())"
                  >
                    {{ showApiKey() ? 'Ocultar' : 'Mostrar' }}
                  </button>
                </div>
              </div>

              <!-- PENSAMIENTO PROFUNDO -->
              <div class="form-group">
                <label class="form-label" for="aura-thinking-toggle">Pensamiento Profundo (Razonamiento)</label>
                @if (currentModelSupportsThinking()) {
                  <div style="display: flex; gap: 10px; align-items: center;">
                    <label for="aura-thinking-toggle" class="toggle-box" style="flex: 1;">
                      <input
                        id="aura-thinking-toggle"
                        type="checkbox"
                        [(ngModel)]="auraConfig.thinkingEnabled"
                      />
                      <span>{{ auraConfig.thinkingEnabled ? 'Activado' : 'Desactivado' }}</span>
                    </label>

                    @if (auraConfig.thinkingEnabled) {
                      <div style="flex: 1;">
                        <label for="aura-reasoning-effort" class="sr-only">Nivel de razonamiento</label>
                        <select
                          id="aura-reasoning-effort"
                          class="form-control"
                          [(ngModel)]="auraConfig.reasoningEffort"
                        >
                          <option value="low">Esfuerzo Bajo</option>
                          <option value="medium">Esfuerzo Medio</option>
                          <option value="high">Esfuerzo Alto</option>
                        </select>
                      </div>
                    }
                  </div>
                } @else {
                  <div class="toggle-box" style="color: var(--text-muted); justify-content: space-between;">
                    <span>No disponible en este modelo (respuesta directa)</span>
                    @if (auraConfig.provider === 'custom') {
                      <label for="aura-force-custom-thinking" style="display: flex; align-items: center; gap: 6px; cursor: pointer; font-size: 12px;">
                        <input
                          id="aura-force-custom-thinking"
                          type="checkbox"
                          [(ngModel)]="auraConfig.customModelForceThinking"
                        />
                        <span>Habilitar manualmente</span>
                      </label>
                    }
                  </div>
                }
              </div>
            </div>

            <!-- PARÁMETROS GENERATIVOS Y PROMPT -->
            <div class="grid-2" style="margin-top: 8px;">
              <div class="form-group">
                <label for="aura-temperature" class="form-label">Temperatura: {{ auraConfig.temperature }}</label>
                <input
                  id="aura-temperature"
                  type="range"
                  class="form-control"
                  style="padding: 10px;"
                  [(ngModel)]="auraConfig.temperature"
                  min="0.1"
                  max="1.0"
                  step="0.05"
                />
              </div>

              <div class="form-group">
                <label for="aura-max-tokens" class="form-label">Máximo de Tokens</label>
                <input
                  id="aura-max-tokens"
                  type="number"
                  class="form-control"
                  [(ngModel)]="auraConfig.maxTokens"
                  min="100"
                  max="4096"
                  step="50"
                />
              </div>
            </div>

            <div class="form-group">
              <label for="aura-system-prompt" class="form-label">Instrucciones del Sistema (System Prompt)</label>
              <textarea
                id="aura-system-prompt"
                class="form-control"
                rows="5"
                [(ngModel)]="auraConfig.systemPrompt"
              ></textarea>
            </div>

            <!-- FUNCIONES DE AURA -->
            <div class="tools-box">
              <h3 style="font-size: 14px; font-weight: 700; margin-bottom: 12px;">Funciones Habilitadas</h3>
              <div class="grid-2">
                <label for="aura-tool-catalog" class="toggle-box">
                  <input id="aura-tool-catalog" type="checkbox" [(ngModel)]="auraConfig.enableCatalogSearch" />
                  <span>Búsqueda en el catálogo</span>
                </label>

                <label for="aura-tool-chat" class="toggle-box">
                  <input id="aura-tool-chat" type="checkbox" [(ngModel)]="auraConfig.enableCommunityChatBot" />
                  <span>Respuestas en el Chat Global (&#64;Aura)</span>
                </label>
              </div>
            </div>

            <div class="card-footer-actions">
              <span></span>
              <button
                type="button"
                class="btn btn-primary"
                (click)="saveAuraConfig()"
                [disabled]="isSaving()"
              >
                {{ isSaving() ? 'Guardando...' : 'Guardar Configuración' }}
              </button>
            </div>
          </section>

          <!-- PRUEBA DE CONEXIÓN -->
          <section class="card" aria-labelledby="heading-brain-test">
            <div class="section-header-row">
              <div>
                <h2 id="heading-brain-test">Probar Conexión</h2>
              </div>
              <button
                type="button"
                class="btn btn-secondary"
                (click)="testBrainNow()"
                [disabled]="isTestingBrain()"
              >
                {{ isTestingBrain() ? 'Probando...' : 'Enviar Mensaje de Prueba' }}
              </button>
            </div>

            <div class="form-group" style="margin-bottom: 0;">
              <label for="aura-test-prompt" class="form-label">Mensaje</label>
              <input
                id="aura-test-prompt"
                type="text"
                class="form-control"
                [(ngModel)]="testPrompt"
                placeholder="Escribe un mensaje para verificar la respuesta del modelo..."
              />
            </div>

            @if (testBrainResult()) {
              <div class="brain-test-result" role="region" aria-label="Respuesta del modelo">
                <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 8px;">
                  <div style="display: flex; gap: 8px; align-items: center;">
                    <span class="badge badge-primary">{{ testBrainResult()?.provider | uppercase }}</span>
                    <span class="badge badge-info">{{ testBrainResult()?.model }}</span>
                  </div>
                  <small style="color: #34d399; font-weight: 700;">{{ testBrainResult()?.latencyMs }} ms</small>
                </div>
                <p style="font-size: 14px; line-height: 1.5; white-space: pre-wrap; margin: 0;">{{ testBrainResult()?.reply }}</p>
              </div>
            }
          </section>
        </div>
      }

      <!-- =====================================================================
           2. PESTAÑA: NOTIFICACIONES PUSH
           ===================================================================== -->
      @if (activeTab() === 'push') {
        <div
          id="tabpanel-push"
          role="tabpanel"
          tabindex="0"
          aria-labelledby="tab-push"
          class="exec-page"
        >
          <section class="card">
            <div class="section-header-row">
              <div>
                <h2>Enviar Notificación Push</h2>
              </div>
              <button
                type="button"
                class="btn btn-secondary btn-sm"
                (click)="optimizePushWithAi()"
                [disabled]="isGeneratingAiPush()"
              >
                {{ isGeneratingAiPush() ? 'Generando...' : 'Redactar con IA' }}
              </button>
            </div>

            <div class="grid-3">
              <div class="form-group">
                <label for="push-destination" class="form-label">Destino</label>
                <select id="push-destination" class="form-control" [(ngModel)]="pushForm.destination">
                  <option value="app">Inicio de la aplicación</option>
                  <option value="content_detail">Ficha de contenido</option>
                  <option value="announcements">Anuncios</option>
                  <option value="global_chat">Chat Global</option>
                  <option value="external_url">Enlace externo</option>
                  <option value="donation">Donaciones</option>
                </select>
              </div>

              <div class="form-group">
                <label for="push-target" class="form-label">Plataforma</label>
                <select id="push-target" class="form-control" [(ngModel)]="pushForm.target">
                  <option value="all">Todas</option>
                  <option value="android">Android</option>
                  <option value="desktop">Escritorio</option>
                </select>
              </div>

              <div class="form-group">
                <label for="push-delay" class="form-label">Retraso de envío (minutos)</label>
                <input
                  id="push-delay"
                  type="number"
                  class="form-control"
                  [(ngModel)]="pushForm.delayMinutes"
                  min="0"
                  max="1440"
                />
              </div>
            </div>

            @if (pushForm.destination === 'content_detail') {
              <div class="grid-2">
                <div class="form-group">
                  <label for="push-content-id" class="form-label">ID o URL del Contenido</label>
                  <input
                    id="push-content-id"
                    type="text"
                    class="form-control"
                    [(ngModel)]="pushForm.contentId"
                    placeholder="Ej. 150"
                  />
                </div>
                <div class="form-group">
                  <label for="push-content-type" class="form-label">Categoría</label>
                  <select id="push-content-type" class="form-control" [(ngModel)]="pushForm.contentType">
                    <option value="peliculas">Películas</option>
                    <option value="series">Series</option>
                    <option value="documentales">Documentales</option>
                    <option value="cortometrajes">Cortometrajes</option>
                  </select>
                </div>
              </div>
            }

            @if (pushForm.destination === 'external_url') {
              <div class="form-group">
                <label for="push-url" class="form-label">URL Externa</label>
                <input
                  id="push-url"
                  type="url"
                  class="form-control"
                  [(ngModel)]="pushForm.url"
                  placeholder="https://..."
                />
              </div>
            }

            <div class="form-group">
              <label for="push-title" class="form-label">Título *</label>
              <input
                id="push-title"
                type="text"
                class="form-control"
                [(ngModel)]="pushForm.title"
                placeholder="Título de la notificación"
              />
            </div>

            <div class="form-group">
              <label for="push-body" class="form-label">Mensaje *</label>
              <textarea
                id="push-body"
                class="form-control"
                rows="3"
                [(ngModel)]="pushForm.body"
                placeholder="Contenido de la notificación..."
              ></textarea>
            </div>

            <div class="card-footer-actions">
              <span></span>
              <button
                type="button"
                class="btn btn-primary"
                (click)="sendPushNotification()"
                [disabled]="isSendingPush() || !pushForm.title.trim() || !pushForm.body.trim()"
              >
                {{ isSendingPush() ? 'Enviando...' : (pushForm.delayMinutes > 0 ? 'Programar Envío' : 'Enviar Notificación') }}
              </button>
            </div>
          </section>

          <section class="card">
            <div class="section-header-row">
              <div>
                <h3>Historial de Envíos</h3>
              </div>
            </div>

            @if (pushHistory().length === 0) {
              <p style="color: var(--text-muted); font-size: 13px; text-align: center; padding: 16px;">
                Sin envíos recientes.
              </p>
            } @else {
              <div class="table-responsive">
                <table class="table">
                  <caption class="sr-only">Historial de notificaciones push</caption>
                  <thead>
                    <tr>
                      <th scope="col">Fecha</th>
                      <th scope="col">Mensaje</th>
                      <th scope="col">Destino</th>
                      <th scope="col">Estado</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (item of pushHistory(); track item.id) {
                      <tr>
                        <td>{{ item.createdAt | date:'short' }}</td>
                        <td>
                          <strong>{{ item.title }}</strong>
                          <div style="font-size: 12px; color: var(--text-muted);">{{ item.body }}</div>
                        </td>
                        <td><span class="badge badge-info">{{ item.destination }}</span></td>
                        <td>
                          @if (item.status === 'SENT') {
                            <span class="badge badge-success">Enviada</span>
                          } @else if (item.status === 'SCHEDULED') {
                            <span class="badge badge-warning">Programada</span>
                          } @else {
                            <span class="badge badge-danger">{{ item.error || 'Error' }}</span>
                          }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </section>
        </div>
      }

      <!-- =====================================================================
           3. PESTAÑA: COLECCIONES Y RANKING
           ===================================================================== -->
      @if (activeTab() === 'collections') {
        <div
          id="tabpanel-collections"
          role="tabpanel"
          tabindex="0"
          aria-labelledby="tab-collections"
          class="exec-page"
        >
          <section class="card">
            <div class="section-header-row">
              <div>
                <h2>Colecciones Temáticas</h2>
                <p>Generadas automáticamente cada 24 horas con el proveedor de IA activo</p>
              </div>
              <button
                type="button"
                class="btn btn-primary"
                (click)="regenerateCollectionsNow()"
                [disabled]="isRegeneratingCollections()"
              >
                {{ isRegeneratingCollections() ? 'Generando...' : 'Regenerar Colecciones' }}
              </button>
            </div>

            @if (!curatedCollections()?.collections?.length) {
              <p style="color: var(--text-muted); padding: 16px; text-align: center;">
                No hay colecciones generadas.
              </p>
            } @else {
              <div class="grid-3">
                @for (col of curatedCollections()?.collections; track col.id) {
                  <div class="collection-card">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
                      <span class="badge badge-primary">{{ col.items?.length || 0 }} títulos</span>
                      <small style="color: var(--text-dim);">{{ curatedCollections()?.updatedAt | date:'shortDate' }}</small>
                    </div>
                    <h4 style="font-size: 15px; font-weight: 700; margin-bottom: 6px;">{{ col.title }}</h4>
                    <p style="font-size: 13px; color: var(--text-muted); margin-bottom: 12px;">{{ col.subtitle || col.description }}</p>
                    <div class="collection-items-list">
                      @for (it of (col.items || []).slice(0, 6); track it.id) {
                        <span class="badge badge-info" style="font-size: 11px; text-transform: none;">
                          {{ it.title || ('ID ' + it.id) }}
                        </span>
                      }
                    </div>
                  </div>
                }
              </div>
            }
          </section>

          <section class="card">
            <div class="section-header-row">
              <div>
                <h2>Ranking Top 10 Semanal</h2>
              </div>
              <button
                type="button"
                class="btn btn-secondary"
                (click)="recalculateRankingNow()"
                [disabled]="isRecalculatingRanking()"
              >
                {{ isRecalculatingRanking() ? 'Calculando...' : 'Recalcular Ranking' }}
              </button>
            </div>

            @if (!weeklyRanking()?.items?.length) {
              <p style="color: var(--text-muted); padding: 16px; text-align: center;">
                Sin datos de ranking.
              </p>
            } @else {
              <div class="table-responsive">
                <table class="table">
                  <caption class="sr-only">Ranking Top 10 Semanal</caption>
                  <thead>
                    <tr>
                      <th scope="col">#</th>
                      <th scope="col">Título</th>
                      <th scope="col">Tipo</th>
                      <th scope="col">Puntaje</th>
                      <th scope="col">Valoración</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (item of weeklyRanking()?.items; track item.rank) {
                      <tr>
                        <td><strong>#{{ item.rank }}</strong></td>
                        <td><strong>{{ item.title }}</strong></td>
                        <td><span class="badge badge-info">{{ item.type }}</span></td>
                        <td>{{ item.score }} pts</td>
                        <td>{{ item.averageRating || '—' }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </section>
        </div>
      }

      <!-- =====================================================================
           4. PESTAÑA: MANTENIMIENTO
           ===================================================================== -->
      @if (activeTab() === 'cron') {
        <section
          id="tabpanel-cron"
          role="tabpanel"
          tabindex="0"
          aria-labelledby="tab-cron"
          class="card"
        >
          <div class="section-header-row">
            <div>
              <h2>Mantenimiento</h2>
            </div>
          </div>

          <div class="grid-2">
            <div class="task-card">
              <h3>Limpieza de Mensajes del Chat</h3>
              <div class="form-group" style="margin-top: 8px;">
                <label for="cron-purge-count" class="form-label">Cantidad de mensajes antiguos a eliminar</label>
                <input
                  id="cron-purge-count"
                  type="number"
                  class="form-control"
                  [(ngModel)]="cronConfig.cleanChatCount"
                  min="10"
                  max="1000"
                />
              </div>

              <button
                type="button"
                class="btn btn-secondary"
                (click)="runPurgeChatNow()"
                [disabled]="isRunningTask()"
              >
                Eliminar {{ cronConfig.cleanChatCount }} mensajes antiguos
              </button>
            </div>

            <div class="task-card">
              <h3>Verificación de Enlaces de Audio</h3>
              <p>Comprueba la disponibilidad HTTP de una muestra del catálogo.</p>

              @if (auditResult()) {
                <div style="padding: 10px; background: rgba(16, 185, 129, 0.1); border-radius: 6px; font-size: 13px; margin: 8px 0;">
                  Verificados {{ auditResult()?.checkedCount }}:
                  <strong>{{ auditResult()?.healthyCount }} activos</strong> •
                  <strong>{{ auditResult()?.brokenCount }} con error</strong>
                </div>
              }

              <div style="margin-top: auto; padding-top: 12px;">
                <button
                  type="button"
                  class="btn btn-secondary"
                  style="width: 100%;"
                  (click)="runAuditLinksNow()"
                  [disabled]="isRunningTask()"
                >
                  Verificar enlaces ahora
                </button>
              </div>
            </div>
          </div>

          <div class="card-footer-actions">
            <span></span>
            <button
              type="button"
              class="btn btn-primary"
              (click)="saveCronConfig()"
              [disabled]="isSaving()"
            >
              Guardar Parámetros
            </button>
          </div>
        </section>
      }
    </div>
  `,
  styles: [`
    .brain-test-result {
      margin-top: 14px;
      padding: 14px 16px;
      border-radius: var(--radius-sm);
      background-color: var(--bg-input);
      border: 1px solid var(--border-color);
    }

    .tools-box {
      padding: 16px;
      background-color: var(--bg-input);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-sm);
    }

    .collection-card {
      background-color: var(--bg-input);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-sm);
      padding: 16px;
      display: flex;
      flex-direction: column;
    }

    .collection-items-list {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      margin-top: auto;
    }

    .task-card {
      background-color: var(--bg-input);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-sm);
      padding: 18px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .task-card h3 {
      font-size: 15px;
      font-weight: 700;
    }

    .task-card p {
      font-size: 13px;
      color: var(--text-muted);
    }
  `],
})
export class AgentsConfigComponent implements OnInit {
  private readonly configService = inject(ConfigService);
  private readonly contentService = inject(ContentService);

  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly isSyncingModels = signal(false);
  readonly isTestingBrain = signal(false);
  readonly isSendingPush = signal(false);
  readonly isGeneratingAiPush = signal(false);
  readonly isRegeneratingCollections = signal(false);
  readonly isRecalculatingRanking = signal(false);
  readonly isRunningTask = signal(false);
  readonly showApiKey = signal(false);

  readonly activeTab = signal<'aura' | 'push' | 'collections' | 'cron'>('aura');
  readonly statusMessage = signal<string | null>(null);
  readonly statusType = signal<'success' | 'error' | 'info'>('success');

  readonly pushHistory = signal<any[]>([]);
  readonly curatedCollections = signal<any | null>(null);
  readonly weeklyRanking = signal<any | null>(null);
  readonly auditResult = signal<any | null>(null);
  readonly testBrainResult = signal<any | null>(null);

  testPrompt = 'Hola Aura, ¿qué proveedor y modelo estás usando?';

  apiKeyInputs: Record<AiProviderType, string> = {
    groq: '',
    gemini: '',
    custom: '',
  };

  readonly keysStatus = signal<Record<AiProviderType, ProviderKeyStatus>>({
    groq: { hasKey: false, maskedKey: null, source: 'none' },
    gemini: { hasKey: false, maskedKey: null, source: 'none' },
    custom: { hasKey: false, maskedKey: null, source: 'none' },
  });

  readonly providerCatalog = signal<AiProviderCatalogEntry[]>([
    {
      id: 'groq',
      order: 1,
      name: 'Groq',
      subtitle: 'api.groq.com/openai/v1',
      badge: 'Groq',
      baseUrl: 'https://api.groq.com/openai/v1',
      docsUrl: 'https://console.groq.com/docs/quickstart',
      keyPlaceholder: 'gsk_...',
      defaultModel: 'openai/gpt-oss-120b',
      models: [
        {
          id: 'openai/gpt-oss-120b',
          name: 'GPT-OSS 120B (openai/gpt-oss-120b)',
          badge: 'Razonamiento',
          description: 'Soporta pensamiento profundo configurable.',
          supportsThinking: true,
          defaultThinking: true,
        },
        {
          id: 'llama-3.3-70b-versatile',
          name: 'Llama 3.3 70B (llama-3.3-70b-versatile)',
          badge: 'Estándar',
          description: 'Respuesta directa.',
          supportsThinking: false,
          defaultThinking: false,
        },
        {
          id: 'qwen/qwen3-32b',
          name: 'Qwen 3 32B (qwen/qwen3-32b)',
          badge: 'Razonamiento',
          description: 'Soporta pensamiento profundo.',
          supportsThinking: true,
          defaultThinking: true,
        },
        {
          id: 'openai/gpt-oss-20b',
          name: 'GPT-OSS 20B (openai/gpt-oss-20b)',
          badge: 'Razonamiento',
          description: 'Soporta pensamiento profundo ligero.',
          supportsThinking: true,
          defaultThinking: false,
        },
        {
          id: 'deepseek-r1-distill-llama-70b',
          name: 'DeepSeek R1 70B (deepseek-r1-distill-llama-70b)',
          badge: 'Razonamiento',
          description: 'Modelo analítico con pensamiento interno.',
          supportsThinking: true,
          defaultThinking: true,
        },
        {
          id: 'meta-llama/llama-4-scout-17b-16e-instruct',
          name: 'Llama 4 Scout 17B (meta-llama/llama-4-scout-17b-16e-instruct)',
          badge: 'Estándar',
          description: 'Respuesta directa.',
          supportsThinking: false,
          defaultThinking: false,
        },
        {
          id: 'llama-3.1-8b-instant',
          name: 'Llama 3.1 8B Instant (llama-3.1-8b-instant)',
          badge: 'Estándar',
          description: 'Respuesta directa rápida.',
          supportsThinking: false,
          defaultThinking: false,
        },
      ],
    },
    {
      id: 'gemini',
      order: 2,
      name: 'Google Gemini',
      subtitle: 'generativelanguage.googleapis.com',
      badge: 'Gemini',
      baseUrl: 'https://generativelanguage.googleapis.com/v1beta',
      docsUrl: 'https://ai.google.dev/gemini-api/docs',
      keyPlaceholder: 'AIzaSy...',
      defaultModel: 'gemini-2.5-flash',
      models: [
        {
          id: 'gemini-2.5-flash',
          name: 'Gemini 2.5 Flash (gemini-2.5-flash)',
          badge: 'Razonamiento',
          description: 'Soporta pensamiento profundo opcional.',
          supportsThinking: true,
          defaultThinking: false,
        },
        {
          id: 'gemma-4-26b-a4b-it',
          name: 'Gemma 4 26B IT (gemma-4-26b-a4b-it)',
          badge: 'Razonamiento',
          description: 'Soporta pensamiento profundo configurable.',
          supportsThinking: true,
          defaultThinking: false,
        },
        {
          id: 'gemini-2.5-pro',
          name: 'Gemini 2.5 Pro (gemini-2.5-pro)',
          badge: 'Razonamiento',
          description: 'Soporta pensamiento profundo avanzado.',
          supportsThinking: true,
          defaultThinking: true,
        },
        {
          id: 'gemini-2.5-flash-lite',
          name: 'Gemini 2.5 Flash-Lite (gemini-2.5-flash-lite)',
          badge: 'Razonamiento',
          description: 'Soporta pensamiento profundo ligero.',
          supportsThinking: true,
          defaultThinking: false,
        },
        {
          id: 'gemini-2.0-flash',
          name: 'Gemini 2.0 Flash (gemini-2.0-flash)',
          badge: 'Estándar',
          description: 'Respuesta directa.',
          supportsThinking: false,
          defaultThinking: false,
        },
      ],
    },
    {
      id: 'custom',
      order: 3,
      name: 'Personalizado',
      subtitle: 'Endpoint compatible con OpenAI',
      badge: 'Personalizado',
      baseUrl: 'https://api.openai.com/v1',
      docsUrl: 'https://platform.openai.com/docs/api-reference/chat',
      keyPlaceholder: 'sk-...',
      defaultModel: 'gpt-4o-mini',
      models: [
        {
          id: 'gpt-4o-mini',
          name: 'GPT-4o Mini (gpt-4o-mini)',
          badge: 'Estándar',
          description: 'Respuesta directa.',
          supportsThinking: false,
          defaultThinking: false,
        },
        {
          id: 'gpt-4o',
          name: 'GPT-4o (gpt-4o)',
          badge: 'Estándar',
          description: 'Respuesta directa.',
          supportsThinking: false,
          defaultThinking: false,
        },
        {
          id: 'o3-mini',
          name: 'OpenAI o3-mini (o3-mini)',
          badge: 'Razonamiento',
          description: 'Soporta pensamiento profundo.',
          supportsThinking: true,
          defaultThinking: true,
        },
        {
          id: 'deepseek-reasoner',
          name: 'DeepSeek R1 (deepseek-reasoner)',
          badge: 'Razonamiento',
          description: 'Soporta pensamiento profundo.',
          supportsThinking: true,
          defaultThinking: true,
        },
      ],
    },
  ]);

  auraConfig: AuraAgentConfig = {
    enabled: true,
    provider: 'groq',
    model: 'openai/gpt-oss-120b',
    thinkingEnabled: true,
    reasoningEffort: 'medium',
    temperature: 0.7,
    maxTokens: 700,
    customBaseUrl: 'https://api.openai.com/v1',
    customModelForceThinking: false,
    systemPrompt: '',
    enableCatalogSearch: true,
    enableCommunityChatBot: true,
    chatMentionKeyword: '@Aura',
  };

  cronConfig: CronTasksConfig = {
    cleanOldChatDays: 30,
    cleanChatCount: 50,
    autoCleanEnabled: true,
    catalogLinkAuditIntervalHours: 24,
    recalculateRankingsDaily: true,
  };

  pushForm: PushNotificationForm = {
    title: '',
    body: '',
    target: 'all',
    destination: 'app',
    contentId: '',
    contentType: 'peliculas',
    url: '',
    delayMinutes: 0,
  };

  ngOnInit() {
    this.loadAll();
  }

  loadAll() {
    this.isLoading.set(true);

    this.configService.getAuraBrain().subscribe({
      next: (res) => {
        if (res?.providerCatalog && Array.isArray(res.providerCatalog)) {
          this.providerCatalog.set(res.providerCatalog);
        }
        if (res?.keysStatus) {
          this.keysStatus.set(res.keysStatus);
        }
        if (res?.config) {
          this.auraConfig = { ...this.auraConfig, ...res.config };
        }
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
      },
    });

    this.configService.getAllConfigs().subscribe({
      next: (items) => {
        for (const item of items) {
          if (item.key === 'cron_tasks_config') {
            this.cronConfig = { ...this.cronConfig, ...item.value };
          }
        }
      },
      error: () => {},
    });

    this.configService.getPushHistory().subscribe({
      next: (history) => this.pushHistory.set(history || []),
      error: () => {},
    });

    this.configService.getCuratedCollections().subscribe({
      next: (cols) => this.curatedCollections.set(cols),
      error: () => {},
    });

    this.configService.getWeeklyRanking().subscribe({
      next: (rank) => this.weeklyRanking.set(rank),
      error: () => {},
    });
  }

  getCurrentProviderEntry(): AiProviderCatalogEntry {
    const list = this.providerCatalog();
    return list.find((p) => p.id === this.auraConfig.provider) || list[0];
  }

  getKeyStatus(provider: AiProviderType): ProviderKeyStatus {
    return (
      this.keysStatus()[provider] || {
        hasKey: false,
        maskedKey: null,
        source: 'none',
      }
    );
  }

  getCurrentModelDefinition(): AiModelDefinition | undefined {
    const entry = this.getCurrentProviderEntry();
    return entry.models.find((m) => m.id === this.auraConfig.model);
  }

  currentModelSupportsThinking(): boolean {
    const def = this.getCurrentModelDefinition();
    if (def) return def.supportsThinking;
    if (this.auraConfig.provider === 'custom' && this.auraConfig.customModelForceThinking) {
      return true;
    }
    const lower = (this.auraConfig.model || '').toLowerCase();
    return (
      lower.includes('gpt-oss') ||
      lower.includes('qwen3') ||
      lower.includes('r1') ||
      lower.includes('reason') ||
      lower.includes('gemini-2.5') ||
      lower.includes('gemma-4') ||
      lower.includes('o3') ||
      lower.includes('o1')
    );
  }

  selectProvider(providerId: AiProviderType) {
    this.auraConfig.provider = providerId;
    const providerEntry = this.getCurrentProviderEntry();
    const firstModel = providerEntry.models[0];
    if (firstModel) {
      this.auraConfig.model = firstModel.id;
      this.auraConfig.thinkingEnabled = firstModel.defaultThinking;
    } else {
      this.auraConfig.model = providerEntry.defaultModel;
    }
    this.testBrainResult.set(null);
  }

  onModelSelectChange(newModelId: string) {
    const entry = this.getCurrentProviderEntry();
    const found = entry.models.find((m) => m.id === newModelId);
    if (found) {
      this.auraConfig.thinkingEnabled = found.defaultThinking;
    }
  }

  syncLiveModelsForCurrentProvider() {
    this.isSyncingModels.set(true);
    this.configService
      .getAuraBrainModels(this.auraConfig.provider, this.auraConfig.customBaseUrl)
      .subscribe({
        next: (res) => {
          this.isSyncingModels.set(false);
          if (res?.models && Array.isArray(res.models) && res.models.length > 0) {
            const updatedCatalog = this.providerCatalog().map((p) =>
              p.id === this.auraConfig.provider ? { ...p, models: res.models } : p,
            );
            this.providerCatalog.set(updatedCatalog);
            if (!res.models.some((m: AiModelDefinition) => m.id === this.auraConfig.model)) {
              this.auraConfig.model = res.models[0].id;
            }
          }
          this.showStatus(res?.message || 'Modelos sincronizados', 'success');
        },
        error: () => {
          this.isSyncingModels.set(false);
          this.showStatus('No se pudo obtener la lista de modelos', 'error');
        },
      });
  }

  saveAuraConfig() {
    this.isSaving.set(true);
    const apiKeysPayload: {
      groqApiKey?: string;
      geminiApiKey?: string;
      customApiKey?: string;
    } = {};

    if (this.apiKeyInputs.groq.trim()) {
      apiKeysPayload.groqApiKey = this.apiKeyInputs.groq.trim();
    }
    if (this.apiKeyInputs.gemini.trim()) {
      apiKeysPayload.geminiApiKey = this.apiKeyInputs.gemini.trim();
    }
    if (this.apiKeyInputs.custom.trim()) {
      apiKeysPayload.customApiKey = this.apiKeyInputs.custom.trim();
    }

    this.configService
      .saveAuraBrain({
        config: this.auraConfig,
        apiKeys: apiKeysPayload,
      })
      .subscribe({
        next: (res) => {
          this.isSaving.set(false);
          if (res?.keysStatus) this.keysStatus.set(res.keysStatus);
          if (res?.config) this.auraConfig = { ...this.auraConfig, ...res.config };
          this.apiKeyInputs = { groq: '', gemini: '', custom: '' };
          this.showApiKey.set(false);
          this.showStatus('Configuración guardada correctamente', 'success');
        },
        error: (err) => {
          this.isSaving.set(false);
          this.showStatus(
            `Error al guardar: ${err?.error?.message || 'Error'}`,
            'error',
          );
        },
      });
  }

  testBrainNow() {
    this.isTestingBrain.set(true);
    this.testBrainResult.set(null);
    const tempKey = this.apiKeyInputs[this.auraConfig.provider]?.trim() || undefined;

    this.configService
      .testAuraBrain({
        prompt: this.testPrompt,
        overrideConfig: this.auraConfig,
        tempApiKey: tempKey,
      })
      .subscribe({
        next: (res) => {
          this.isTestingBrain.set(false);
          this.testBrainResult.set(res);
          this.showStatus(`Respuesta recibida (${res.latencyMs} ms)`, 'success');
        },
        error: (err) => {
          this.isTestingBrain.set(false);
          this.showStatus(
            err?.error?.message || 'Error al probar conexión. Verifica la Clave API.',
            'error',
          );
        },
      });
  }

  optimizePushWithAi() {
    this.isGeneratingAiPush.set(true);
    this.configService
      .generatePushWithAi({
        draftTitle: this.pushForm.title || 'Novedades en Audiocinemateca',
        draftMessage: this.pushForm.body || 'Nuevos títulos disponibles en el catálogo.',
        contextType: 'push',
      })
      .subscribe({
        next: (res) => {
          this.isGeneratingAiPush.set(false);
          if (res?.pushTitle) this.pushForm.title = res.pushTitle;
          if (res?.pushBody) this.pushForm.body = res.pushBody;
          this.showStatus('Texto generado con IA', 'success');
        },
        error: () => {
          this.isGeneratingAiPush.set(false);
          this.showStatus('No se pudo generar el texto con IA', 'error');
        },
      });
  }

  sendPushNotification() {
    if (!this.pushForm.title.trim() || !this.pushForm.body.trim()) return;
    this.isSendingPush.set(true);

    this.configService
      .sendPushNotification({
        title: this.pushForm.title.trim(),
        body: this.pushForm.body.trim(),
        destination: this.pushForm.destination,
        target: this.pushForm.target,
        contentId: this.pushForm.contentId || undefined,
        contentType: this.pushForm.contentType || undefined,
        url: this.pushForm.url || undefined,
        delayMinutes: Number(this.pushForm.delayMinutes) || 0,
      })
      .subscribe({
        next: (res) => {
          this.isSendingPush.set(false);
          this.showStatus(res?.message || 'Notificación enviada', 'success');
          this.pushForm.title = '';
          this.pushForm.body = '';
          this.configService.getPushHistory().subscribe({
            next: (h) => this.pushHistory.set(h || []),
            error: () => {},
          });
        },
        error: (err) => {
          this.isSendingPush.set(false);
          this.showStatus(`Error: ${err?.error?.message || err?.message || 'Error'}`, 'error');
        },
      });
  }

  regenerateCollectionsNow() {
    this.isRegeneratingCollections.set(true);
    this.configService.regenerateCuratedCollections().subscribe({
      next: (res) => {
        this.isRegeneratingCollections.set(false);
        this.curatedCollections.set(res);
        this.showStatus('Colecciones actualizadas', 'success');
      },
      error: () => {
        this.isRegeneratingCollections.set(false);
        this.showStatus('Error al regenerar colecciones', 'error');
      },
    });
  }

  recalculateRankingNow() {
    this.isRecalculatingRanking.set(true);
    this.configService.recalculateWeeklyRanking().subscribe({
      next: (res) => {
        this.isRecalculatingRanking.set(false);
        this.weeklyRanking.set(res);
        this.showStatus('Ranking actualizado', 'success');
      },
      error: () => {
        this.isRecalculatingRanking.set(false);
        this.showStatus('Error al recalcular el ranking', 'error');
      },
    });
  }

  runPurgeChatNow() {
    this.isRunningTask.set(true);
    this.configService.purgeChatMessages({ count: this.cronConfig.cleanChatCount || 50 }).subscribe({
      next: (res) => {
        this.isRunningTask.set(false);
        this.showStatus(res?.message || 'Mensajes eliminados', 'success');
      },
      error: () => {
        this.isRunningTask.set(false);
        this.showStatus('Error al eliminar mensajes', 'error');
      },
    });
  }

  runAuditLinksNow() {
    this.isRunningTask.set(true);
    this.contentService.auditCatalogLinks(20).subscribe({
      next: (res) => {
        this.isRunningTask.set(false);
        this.auditResult.set(res);
        this.showStatus(
          `Verificación completada: ${res?.healthyCount || 0}/${res?.checkedCount || 0} activos`,
          'success',
        );
      },
      error: () => {
        this.isRunningTask.set(false);
        this.showStatus('Error al verificar enlaces', 'error');
      },
    });
  }

  saveCronConfig() {
    this.isSaving.set(true);
    this.configService
      .updateConfig('cron_tasks_config', this.cronConfig, 'Configuración de mantenimiento')
      .subscribe({
        next: () => {
          this.isSaving.set(false);
          this.showStatus('Parámetros guardados', 'success');
        },
        error: () => {
          this.isSaving.set(false);
          this.showStatus('Error al guardar parámetros', 'error');
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
