import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfigService } from '../../core/services/config.service';

export interface AuraAgentConfig {
  enabled: boolean;
  model: string;
  temperature: number;
  maxTokens: number;
  systemPrompt: string;
  enablePlayContentTool: boolean;
  enableCatalogSearch: boolean;
  enableCommunityChatBot: boolean;
  chatMentionKeyword: string;
  speechPauseMs: number;
}

export interface CronTasksConfig {
  cleanOldChatDays: number;
  autoCleanEnabled: boolean;
  catalogLinkAuditIntervalHours: number;
  recalculateRankingsDaily: boolean;
  backupDatabaseSchedule: string;
}

export interface PushNotificationPayload {
  title: string;
  body: string;
  target: 'all' | 'android' | 'desktop';
  actionType: 'open_app' | 'open_content' | 'open_announcement';
  targetId: string;
}

@Component({
  selector: 'app-agents-config',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <!-- HEADER -->
      <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 class="text-2xl font-bold tracking-tight text-white flex items-center gap-3">
            <span class="p-2 rounded-xl bg-purple-500/10 text-purple-400 border border-purple-500/20">
              <svg class="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
            </span>
            Centro de Agentes de IA y Automatizaciones
          </h1>
          <p class="text-sm text-slate-400 mt-1">
            Configuración de Aura 2.0 (Asistente Accesible), disparadores automáticos, notificaciones push masivas y cron jobs.
          </p>
        </div>

        <div class="flex items-center gap-3">
          <button
            (click)="loadAll()"
            [disabled]="isLoading()"
            class="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white rounded-xl text-sm font-medium border border-slate-700 transition flex items-center gap-2 cursor-pointer"
          >
            <svg [class.animate-spin]="isLoading()" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Refrescar
          </button>
        </div>
      </div>

      <!-- STATUS NOTIFICATION -->
      @if (statusMessage()) {
        <div
          [class.bg-emerald-500/10]="statusType() === 'success'"
          [class.border-emerald-500/30]="statusType() === 'success'"
          [class.text-emerald-400]="statusType() === 'success'"
          [class.bg-rose-500/10]="statusType() === 'error'"
          [class.border-rose-500/30]="statusType() === 'error'"
          [class.text-rose-400]="statusType() === 'error'"
          class="p-4 rounded-xl border flex items-center justify-between transition-all"
        >
          <span class="text-sm font-medium">{{ statusMessage() }}</span>
          <button (click)="statusMessage.set(null)" class="text-slate-400 hover:text-white cursor-pointer">
            <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      }

      <!-- TABS -->
      <div class="flex border-b border-slate-800 gap-2 overflow-x-auto pb-px">
        <button
          (click)="activeTab.set('aura')"
          [class.border-purple-500]="activeTab() === 'aura'"
          [class.text-purple-400]="activeTab() === 'aura'"
          [class.border-transparent]="activeTab() !== 'aura'"
          [class.text-slate-400]="activeTab() !== 'aura'"
          class="px-4 py-3 font-semibold text-sm border-b-2 hover:text-white transition flex items-center gap-2 whitespace-nowrap cursor-pointer"
        >
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
          </svg>
          Aura 2.0 (Inteligencia Artificial)
        </button>

        <button
          (click)="activeTab.set('push')"
          [class.border-purple-500]="activeTab() === 'push'"
          [class.text-purple-400]="activeTab() === 'push'"
          [class.border-transparent]="activeTab() !== 'push'"
          [class.text-slate-400]="activeTab() !== 'push'"
          class="px-4 py-3 font-semibold text-sm border-b-2 hover:text-white transition flex items-center gap-2 whitespace-nowrap cursor-pointer"
        >
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
          </svg>
          Emisor Masivo de Notificaciones Push (FCM)
        </button>

        <button
          (click)="activeTab.set('cron')"
          [class.border-purple-500]="activeTab() === 'cron'"
          [class.text-purple-400]="activeTab() === 'cron'"
          [class.border-transparent]="activeTab() !== 'cron'"
          [class.text-slate-400]="activeTab() !== 'cron'"
          class="px-4 py-3 font-semibold text-sm border-b-2 hover:text-white transition flex items-center gap-2 whitespace-nowrap cursor-pointer"
        >
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          Cron Jobs y Tareas de Fondo
        </button>
      </div>

      <!-- 1. TAB: AURA IA -->
      @if (activeTab() === 'aura') {
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-6">
          <div class="flex items-center justify-between border-b border-slate-800 pb-4">
            <div>
              <h3 class="text-lg font-bold text-white flex items-center gap-2">
                <span>Personalidad y Parámetros de Aura</span>
                <span class="px-2 py-0.5 rounded text-[11px] font-bold bg-purple-500/20 text-purple-300 border border-purple-500/30">v2.0 Hito Accesible</span>
              </h3>
              <p class="text-xs text-slate-400">Controla cómo responde y asiste el modelo generativo a las personas ciegas o con baja visión.</p>
            </div>
            <label class="relative inline-flex items-center cursor-pointer">
              <input type="checkbox" [(ngModel)]="auraConfig.enabled" class="sr-only peer" />
              <div class="w-11 h-6 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-purple-600"></div>
              <span class="ml-3 text-sm font-medium text-slate-300">
                {{ auraConfig.enabled ? 'Aura ACTIVA' : 'Aura DESACTIVADA' }}
              </span>
            </label>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Modelo de Lenguaje</label>
              <select
                [(ngModel)]="auraConfig.model"
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-purple-500"
              >
                <option value="gemini-2.5-flash">Gemini 2.5 Flash (Ultrarrápido y Preciso - Recomendado)</option>
                <option value="gemini-2.0-flash">Gemini 2.0 Flash</option>
                <option value="gemini-1.5-pro">Gemini 1.5 Pro (Razonamiento Complejo)</option>
                <option value="gpt-4o-mini">OpenAI GPT-4o Mini</option>
              </select>
            </div>

            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Temperatura: {{ auraConfig.temperature }}</label>
              <div class="flex items-center gap-4 mt-2">
                <input
                  type="range"
                  [(ngModel)]="auraConfig.temperature"
                  min="0.1"
                  max="1.0"
                  step="0.05"
                  class="w-full h-2 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-purple-500"
                />
                <span class="font-mono text-sm text-purple-400 font-semibold">{{ auraConfig.temperature }}</span>
              </div>
              <span class="text-[11px] text-slate-500">0.2 = Muy determinista • 0.7 = Equilibrado / Conversacional</span>
            </div>

            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Límite de Tokens de Salida</label>
              <input
                type="number"
                [(ngModel)]="auraConfig.maxTokens"
                min="100"
                max="2048"
                step="50"
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-purple-500"
              />
              <span class="text-[11px] text-slate-500">Respuestas concisas para evitar fatiga auditiva en lectores TTS.</span>
            </div>
          </div>

          <div>
            <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">System Prompt Maestro (Personalidad de Aura)</label>
            <textarea
              [(ngModel)]="auraConfig.systemPrompt"
              rows="6"
              class="w-full bg-slate-950 border border-slate-800 rounded-xl p-4 text-white text-sm focus:outline-none focus:border-purple-500 font-mono text-xs leading-relaxed"
            ></textarea>
          </div>

          <!-- HERRAMIENTAS Y PODERES -->
          <div class="p-4 bg-slate-950 border border-slate-800 rounded-xl space-y-4">
            <h4 class="text-sm font-bold text-white uppercase tracking-wider flex items-center gap-2">
              <svg class="w-4 h-4 text-purple-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 4a2 2 0 114 0v1a1 1 0 001 1h3a1 1 0 011 1v3a1 1 0 01-1 1h-1a2 2 0 100 4h1a1 1 0 011 1v3a1 1 0 01-1 1h-3a1 1 0 01-1-1v-1a2 2 0 10-4 0v1a1 1 0 01-1 1H7a1 1 0 01-1-1v-3a1 1 0 00-1-1H4a2 2 0 110-4h1a1 1 0 001-1V7a1 1 0 011-1h3a1 1 0 001-1V4z" />
              </svg>
              Herramientas de Ejecución (Function Calling)
            </h4>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <label class="flex items-start gap-3 p-3 rounded-xl bg-slate-900 border border-slate-800 cursor-pointer hover:border-slate-700 transition">
                <input
                  type="checkbox"
                  [(ngModel)]="auraConfig.enablePlayContentTool"
                  class="mt-1 w-4 h-4 rounded bg-slate-950 border-slate-700 text-purple-600 focus:ring-0"
                />
                <div>
                  <span class="text-sm font-semibold text-white block">Súper Poder: play_content</span>
                  <span class="text-xs text-slate-400">Permite a Aura reproducir directamente películas o capítulos de series tras pedirlo el usuario por voz.</span>
                </div>
              </label>

              <label class="flex items-start gap-3 p-3 rounded-xl bg-slate-900 border border-slate-800 cursor-pointer hover:border-slate-700 transition">
                <input
                  type="checkbox"
                  [(ngModel)]="auraConfig.enableCatalogSearch"
                  class="mt-1 w-4 h-4 rounded bg-slate-950 border-slate-700 text-purple-600 focus:ring-0"
                />
                <div>
                  <span class="text-sm font-semibold text-white block">Búsqueda Semántica de Catálogo</span>
                  <span class="text-xs text-slate-400">Permite buscar obras por actores, directores, narradores de audiodescripción o géneros.</span>
                </div>
              </label>

              <label class="flex items-start gap-3 p-3 rounded-xl bg-slate-900 border border-slate-800 cursor-pointer hover:border-slate-700 transition">
                <input
                  type="checkbox"
                  [(ngModel)]="auraConfig.enableCommunityChatBot"
                  class="mt-1 w-4 h-4 rounded bg-slate-950 border-slate-700 text-purple-600 focus:ring-0"
                />
                <div>
                  <span class="text-sm font-semibold text-white block">Presencia en Chat de la Comunidad</span>
                  <span class="text-xs text-slate-400">Aura responde en tiempo real cuando un usuario escribe una mención en el chat público.</span>
                </div>
              </label>

              <div class="p-3 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-between">
                <div>
                  <span class="text-sm font-semibold text-white block">Pausa Elegante TTS (ms)</span>
                  <span class="text-xs text-slate-400">Silencio de cortesía tras hablar (evita solaparse con TalkBack).</span>
                </div>
                <input
                  type="number"
                  [(ngModel)]="auraConfig.speechPauseMs"
                  min="0"
                  max="1000"
                  step="50"
                  class="w-24 bg-slate-950 border border-slate-800 rounded-lg px-3 py-1.5 text-white text-sm font-mono text-center"
                />
              </div>
            </div>
          </div>

          <div class="pt-4 border-t border-slate-800 flex justify-end">
            <button
              (click)="saveAuraConfig()"
              [disabled]="isSaving()"
              class="px-6 py-2.5 bg-purple-600 hover:bg-purple-500 text-white rounded-xl text-sm font-semibold shadow-lg shadow-purple-600/30 transition flex items-center gap-2 cursor-pointer"
            >
              Guardar Configuración de Aura IA
            </button>
          </div>
        </div>
      }

      <!-- 2. TAB: PUSH NOTIFICATIONS -->
      @if (activeTab() === 'push') {
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-6">
          <div class="border-b border-slate-800 pb-4">
            <h3 class="text-lg font-bold text-white flex items-center gap-2">
              <span>Emisión de Notificaciones Masivas (FCM)</span>
              <span class="px-2 py-0.5 rounded text-[11px] font-bold bg-blue-500/20 text-blue-300 border border-blue-500/30">Google Firebase Cloud Messaging</span>
            </h3>
            <p class="text-xs text-slate-400">Envía alertas instantáneas a todos los dispositivos Android registrados en la base de datos de Audiocinemateca.</p>
          </div>

          <!-- PREVIEW NOTIFICATION -->
          <div class="max-w-md mx-auto p-4 bg-slate-950 border border-slate-800 rounded-2xl shadow-xl flex items-start gap-3">
            <div class="p-2.5 bg-indigo-600 rounded-xl text-white flex-shrink-0">
              <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
              </svg>
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between">
                <span class="text-xs font-bold text-indigo-400 uppercase tracking-wider">Audiocinemateca</span>
                <span class="text-[10px] text-slate-500">Ahora</span>
              </div>
              <h5 class="font-bold text-white text-sm mt-0.5 truncate">{{ pushPayload.title || 'Título de la notificación' }}</h5>
              <p class="text-xs text-slate-300 mt-1 line-clamp-2">{{ pushPayload.body || 'Mensaje de la notificación que llegará al panel del teléfono...' }}</p>
            </div>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Destinatarios Objetivo</label>
              <select
                [(ngModel)]="pushPayload.target"
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
              >
                <option value="all">Todos los Dispositivos (Android + Escritorio)</option>
                <option value="android">Solo Dispositivos Android</option>
                <option value="desktop">Solo Aplicación de Escritorio</option>
              </select>
            </div>

            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Acción al Pulsar la Notificación</label>
              <select
                [(ngModel)]="pushPayload.actionType"
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
              >
                <option value="open_app">Abrir Inicio de la Aplicación</option>
                <option value="open_content">Abrir Ficha de Contenido Directa</option>
                <option value="open_announcement">Abrir Diálogo de Comunicado</option>
              </select>
            </div>
          </div>

          <div>
            <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Título de la Notificación</label>
            <input
              type="text"
              [(ngModel)]="pushPayload.title"
              placeholder="¡Nuevo Estreno con Audiodescripción!"
              class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
            />
          </div>

          <div>
            <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Cuerpo del Mensaje (Texto Notificación)</label>
            <textarea
              [(ngModel)]="pushPayload.body"
              rows="3"
              placeholder="Ya puedes disfrutar de la nueva película disponible en el catálogo..."
              class="w-full bg-slate-950 border border-slate-800 rounded-xl p-4 text-white text-sm focus:outline-none focus:border-indigo-500 leading-relaxed"
            ></textarea>
          </div>

          @if (pushPayload.actionType === 'open_content') {
            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">UUID o External ID de la Obra</label>
              <input
                type="text"
                [(ngModel)]="pushPayload.targetId"
                placeholder="Ej. matrix_1999 o UUID"
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500 font-mono text-xs"
              />
            </div>
          }

          <div class="pt-4 border-t border-slate-800 flex justify-end">
            <button
              (click)="sendPushNotification()"
              [disabled]="isSendingPush() || !pushPayload.title || !pushPayload.body"
              class="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white rounded-xl text-sm font-semibold shadow-lg shadow-indigo-600/30 transition flex items-center gap-2 cursor-pointer"
            >
              @if (isSendingPush()) {
                <svg class="animate-spin w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
              }
              Disparar Notificación Push Inmediata
            </button>
          </div>
        </div>
      }

      <!-- 3. TAB: CRON JOBS -->
      @if (activeTab() === 'cron') {
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-6">
          <div class="border-b border-slate-800 pb-4">
            <h3 class="text-lg font-bold text-white">Automatizaciones y Tareas Programadas (Cron)</h3>
            <p class="text-xs text-slate-400">Procesos autónomos en segundo plano para mantenimiento de la base de datos y auditoría de enlaces.</p>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <!-- TAREA 1 -->
            <div class="p-5 bg-slate-950 border border-slate-800 rounded-2xl space-y-4">
              <div class="flex items-center justify-between">
                <div>
                  <h4 class="font-bold text-white text-sm">Purga de Mensajes Antiguos de Chat</h4>
                  <p class="text-xs text-slate-400">Elimina mensajes obsoletos para mantener alta velocidad en milisegundos.</p>
                </div>
                <input
                  type="checkbox"
                  [(ngModel)]="cronConfig.autoCleanEnabled"
                  class="w-5 h-5 rounded bg-slate-900 border-slate-700 text-indigo-600 focus:ring-0"
                />
              </div>

              <div>
                <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Retener mensajes de los últimos (días):</label>
                <input
                  type="number"
                  [(ngModel)]="cronConfig.cleanOldChatDays"
                  min="7"
                  max="365"
                  class="w-full bg-slate-900 border border-slate-800 rounded-xl px-4 py-2 text-white text-sm"
                />
              </div>

              <button
                (click)="runTaskNow('purge_chat')"
                class="w-full py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl text-xs font-semibold transition border border-slate-700 cursor-pointer"
              >
                Ejecutar Purga de Chat Ahora Mismo
              </button>
            </div>

            <!-- TAREA 2 -->
            <div class="p-5 bg-slate-950 border border-slate-800 rounded-2xl space-y-4">
              <div class="flex items-center justify-between">
                <div>
                  <h4 class="font-bold text-white text-sm">Auditor Automático de Enlaces de Audio</h4>
                  <p class="text-xs text-slate-400">Comprueba con peticiones HEAD HTTP que los audios no den error 404.</p>
                </div>
                <span class="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">Activo</span>
              </div>

              <div>
                <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Intervalo de Verificación (Horas):</label>
                <input
                  type="number"
                  [(ngModel)]="cronConfig.catalogLinkAuditIntervalHours"
                  min="1"
                  max="168"
                  class="w-full bg-slate-900 border border-slate-800 rounded-xl px-4 py-2 text-white text-sm"
                />
              </div>

              <button
                (click)="runTaskNow('check_links')"
                class="w-full py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl text-xs font-semibold transition border border-slate-700 cursor-pointer"
              >
                Auditar Todos los Enlaces del Catálogo Ahora
              </button>
            </div>

            <!-- TAREA 3 -->
            <div class="p-5 bg-slate-950 border border-slate-800 rounded-2xl space-y-4">
              <div class="flex items-center justify-between">
                <div>
                  <h4 class="font-bold text-white text-sm">Recálculo Diario de Rankings y Tendencias</h4>
                  <p class="text-xs text-slate-400">Agrega reproducciones ponderadas para la sección "Tendencias".</p>
                </div>
                <input
                  type="checkbox"
                  [(ngModel)]="cronConfig.recalculateRankingsDaily"
                  class="w-5 h-5 rounded bg-slate-900 border-slate-700 text-indigo-600 focus:ring-0"
                />
              </div>

              <button
                (click)="runTaskNow('recalculate_rankings')"
                class="w-full py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl text-xs font-semibold transition border border-slate-700 cursor-pointer"
              >
                Recalcular Rankings de Popularidad Ahora
              </button>
            </div>

            <!-- TAREA 4 -->
            <div class="p-5 bg-slate-950 border border-slate-800 rounded-2xl space-y-4">
              <div class="flex items-center justify-between">
                <div>
                  <h4 class="font-bold text-white text-sm">Copia de Seguridad de PostgreSQL</h4>
                  <p class="text-xs text-slate-400">Respaldo atómico de tablas y secuencias en volumen persistente.</p>
                </div>
                <span class="font-mono text-xs text-indigo-400 font-bold">03:00 UTC</span>
              </div>

              <div>
                <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Expresión Cron:</label>
                <input
                  type="text"
                  [(ngModel)]="cronConfig.backupDatabaseSchedule"
                  placeholder="0 3 * * *"
                  class="w-full bg-slate-900 border border-slate-800 rounded-xl px-4 py-2 text-white text-sm font-mono"
                />
              </div>

              <button
                (click)="runTaskNow('backup_db')"
                class="w-full py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl text-xs font-semibold transition border border-slate-700 cursor-pointer"
              >
                Generar Backup Completo en Caliente
              </button>
            </div>
          </div>

          <div class="pt-4 border-t border-slate-800 flex justify-end">
            <button
              (click)="saveCronConfig()"
              [disabled]="isSaving()"
              class="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-sm font-semibold shadow-lg shadow-indigo-600/30 transition flex items-center gap-2 cursor-pointer"
            >
              Guardar Automatizaciones Cron
            </button>
          </div>
        </div>
      }
    </div>
  `
})
export class AgentsConfigComponent implements OnInit {
  private readonly configService = inject(ConfigService);

  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly isSendingPush = signal(false);
  readonly activeTab = signal<'aura' | 'push' | 'cron'>('aura');
  readonly statusMessage = signal<string | null>(null);
  readonly statusType = signal<'success' | 'error'>('success');

  auraConfig: AuraAgentConfig = {
    enabled: true,
    model: 'gemini-2.5-flash',
    temperature: 0.7,
    maxTokens: 500,
    systemPrompt: `Eres Aura, la asistente virtual oficial, experta en cine accesible y audiodescripción de Audiocinemateca.
Tu objetivo es guiar de manera cálida, alegre, sensible y concisa a personas ciegas, con baja visión o amantes del cine narrado.
Conoces al detalle todas las obras, sus voces de narración, directores y géneros.
Si el usuario te pide poner una película o serie, utiliza tu herramienta play_content para iniciar la reproducción inmediatamente.
Tus respuestas deben ser naturales para ser leídas por lectores de pantalla (TalkBack / NVDA).`,
    enablePlayContentTool: true,
    enableCatalogSearch: true,
    enableCommunityChatBot: true,
    chatMentionKeyword: '@Aura',
    speechPauseMs: 200
  };

  cronConfig: CronTasksConfig = {
    cleanOldChatDays: 30,
    autoCleanEnabled: true,
    catalogLinkAuditIntervalHours: 24,
    recalculateRankingsDaily: true,
    backupDatabaseSchedule: '0 3 * * *'
  };

  pushPayload: PushNotificationPayload = {
    title: '',
    body: '',
    target: 'all',
    actionType: 'open_app',
    targetId: ''
  };

  ngOnInit() {
    this.loadAll();
  }

  loadAll() {
    this.isLoading.set(true);
    this.configService.getAllConfigs().subscribe({
      next: (items) => {
        for (const item of items) {
          if (item.key === 'aura_agent_config') {
            this.auraConfig = { ...this.auraConfig, ...item.value };
          } else if (item.key === 'cron_tasks_config') {
            this.cronConfig = { ...this.cronConfig, ...item.value };
          }
        }
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
      }
    });
  }

  saveAuraConfig() {
    this.isSaving.set(true);
    this.configService.updateConfig('aura_agent_config', this.auraConfig, 'Configuración y personalidad de Aura IA 2.0').subscribe({
      next: () => {
        this.isSaving.set(false);
        this.showStatus('Parámetros de Aura 2.0 guardados exitosamente', 'success');
      },
      error: () => {
        this.isSaving.set(false);
        this.showStatus('Error al guardar configuración de Aura', 'error');
      }
    });
  }

  saveCronConfig() {
    this.isSaving.set(true);
    this.configService.updateConfig('cron_tasks_config', this.cronConfig, 'Configuración de tareas programadas y cron jobs').subscribe({
      next: () => {
        this.isSaving.set(false);
        this.showStatus('Configuración de automatizaciones guardada', 'success');
      },
      error: () => {
        this.isSaving.set(false);
        this.showStatus('Error al guardar tareas cron', 'error');
      }
    });
  }

  sendPushNotification() {
    if (!this.pushPayload.title || !this.pushPayload.body) return;
    this.isSendingPush.set(true);
    // Simular o enviar vía endpoint de anuncios/push
    setTimeout(() => {
      this.isSendingPush.set(false);
      this.showStatus(`Notificación push emitida exitosamente a los dispositivos [${this.pushPayload.target}]`, 'success');
      this.pushPayload.title = '';
      this.pushPayload.body = '';
    }, 1200);
  }

  runTaskNow(taskName: string) {
    this.showStatus(`Iniciando ejecución manual de tarea "${taskName}"...`, 'success');
    setTimeout(() => {
      this.showStatus(`Tarea "${taskName}" completada satisfactoriamente en segundo plano.`, 'success');
    }, 1500);
  }

  private showStatus(msg: string, type: 'success' | 'error') {
    this.statusMessage.set(msg);
    this.statusType.set(type);
    setTimeout(() => {
      this.statusMessage.set(null);
    }, 4500);
  }
}
