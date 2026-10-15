import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfigService } from '../../core/services/config.service';
import {
  VersionConfig,
  BannerConfig,
  MaintenanceConfig,
  ChatConfig,
  DonationConfig,
  AppConfigItem
} from '../../core/models/config.models';

@Component({
  selector: 'app-config-editor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <!-- HEADER -->
      <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 class="text-2xl font-bold tracking-tight text-white flex items-center gap-3">
            <span class="p-2 rounded-xl bg-violet-500/10 text-violet-400 border border-violet-500/20">
              <svg class="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
            </span>
            Configuración Remota del Sistema
          </h1>
          <p class="text-sm text-slate-400 mt-1">
            Control de versiones de la app Android/Escritorio, avisos globales, mantenimiento y parámetros en vivo (<1ms).
          </p>
        </div>

        <div class="flex items-center gap-3">
          <button
            (click)="seedDefaults()"
            [disabled]="isSaving()"
            class="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white rounded-xl text-sm font-medium border border-slate-700 transition flex items-center gap-2 cursor-pointer"
          >
            <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Restaurar Valores por Defecto
          </button>
          <button
            (click)="loadConfigs()"
            [disabled]="isLoading()"
            class="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-sm font-semibold shadow-lg shadow-indigo-600/30 transition flex items-center gap-2 cursor-pointer"
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
          <div class="flex items-center gap-3">
            @if (statusType() === 'success') {
              <svg class="w-5 h-5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
              </svg>
            } @else {
              <svg class="w-5 h-5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
            }
            <span class="text-sm font-medium">{{ statusMessage() }}</span>
          </div>
          <button (click)="statusMessage.set(null)" class="text-slate-400 hover:text-white cursor-pointer">
            <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      }

      <!-- TABS NAVIGATION -->
      <div class="flex border-b border-slate-800 gap-2 overflow-x-auto pb-px">
        <button
          (click)="activeTab.set('version')"
          [class.border-indigo-500]="activeTab() === 'version'"
          [class.text-indigo-400]="activeTab() === 'version'"
          [class.border-transparent]="activeTab() !== 'version'"
          [class.text-slate-400]="activeTab() !== 'version'"
          class="px-4 py-3 font-semibold text-sm border-b-2 hover:text-white transition flex items-center gap-2 whitespace-nowrap cursor-pointer"
        >
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
          </svg>
          Actualizador de la App (APK)
        </button>

        <button
          (click)="activeTab.set('banner')"
          [class.border-indigo-500]="activeTab() === 'banner'"
          [class.text-indigo-400]="activeTab() === 'banner'"
          [class.border-transparent]="activeTab() !== 'banner'"
          [class.text-slate-400]="activeTab() !== 'banner'"
          class="px-4 py-3 font-semibold text-sm border-b-2 hover:text-white transition flex items-center gap-2 whitespace-nowrap cursor-pointer"
        >
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5.882V19.24a1.76 1.76 0 01-3.417.592l-2.147-6.15M18 13a3 3 0 100-6M5.436 13.683A4.001 4.001 0 017 6h1.832c4.1 0 7.625-1.234 9.168-3v14c-1.543-1.766-5.067-3-9.168-3H7a3.988 3.988 0 01-1.564-.317z" />
          </svg>
          Banner Global del Feed
        </button>

        <button
          (click)="activeTab.set('maintenance')"
          [class.border-indigo-500]="activeTab() === 'maintenance'"
          [class.text-indigo-400]="activeTab() === 'maintenance'"
          [class.border-transparent]="activeTab() !== 'maintenance'"
          [class.text-slate-400]="activeTab() !== 'maintenance'"
          class="px-4 py-3 font-semibold text-sm border-b-2 hover:text-white transition flex items-center gap-2 whitespace-nowrap cursor-pointer"
        >
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          Modo Mantenimiento
        </button>

        <button
          (click)="activeTab.set('chat')"
          [class.border-indigo-500]="activeTab() === 'chat'"
          [class.text-indigo-400]="activeTab() === 'chat'"
          [class.border-transparent]="activeTab() !== 'chat'"
          [class.text-slate-400]="activeTab() !== 'chat'"
          class="px-4 py-3 font-semibold text-sm border-b-2 hover:text-white transition flex items-center gap-2 whitespace-nowrap cursor-pointer"
        >
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
          </svg>
          Chat y Comunidad
        </button>

        <button
          (click)="activeTab.set('donations')"
          [class.border-indigo-500]="activeTab() === 'donations'"
          [class.text-indigo-400]="activeTab() === 'donations'"
          [class.border-transparent]="activeTab() !== 'donations'"
          [class.text-slate-400]="activeTab() !== 'donations'"
          class="px-4 py-3 font-semibold text-sm border-b-2 hover:text-white transition flex items-center gap-2 whitespace-nowrap cursor-pointer"
        >
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
          </svg>
          Donaciones y Enlaces
        </button>

        <button
          (click)="activeTab.set('raw')"
          [class.border-indigo-500]="activeTab() === 'raw'"
          [class.text-indigo-400]="activeTab() === 'raw'"
          [class.border-transparent]="activeTab() !== 'raw'"
          [class.text-slate-400]="activeTab() !== 'raw'"
          class="px-4 py-3 font-semibold text-sm border-b-2 hover:text-white transition flex items-center gap-2 whitespace-nowrap cursor-pointer"
        >
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
          </svg>
          Visor de Base de Datos (Claves Raw)
        </button>
      </div>

      <!-- 1. TAB: VERSION CONFIG -->
      @if (activeTab() === 'version') {
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-6">
          <div class="flex items-center justify-between border-b border-slate-800 pb-4">
            <div>
              <h3 class="text-lg font-bold text-white">Actualizador Oficial de la App</h3>
              <p class="text-xs text-slate-400">Controla las alertas de actualización que recibe la app de Android o de escritorio al iniciarse.</p>
            </div>
            <span class="px-3 py-1 bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 rounded-full text-xs font-semibold">
              Versión Actual: {{ versionConfig.version }}
            </span>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Versión Semántica (ej. 3.1.0)</label>
              <input
                type="text"
                [(ngModel)]="versionConfig.version"
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Código de Versión (versionCode)</label>
              <input
                type="number"
                [(ngModel)]="versionConfig.versionCode"
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Versión Mínima Requerida</label>
              <input
                type="text"
                [(ngModel)]="versionConfig.minRequiredVersion"
                placeholder="3.0.0"
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
              />
              <span class="text-[11px] text-slate-500">Versiones inferiores a esta serán forzadas a actualizarse.</span>
            </div>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Nombre Visible de la Release</label>
              <input
                type="text"
                [(ngModel)]="versionConfig.name"
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Contador de Descargas Mostrado</label>
              <input
                type="number"
                [(ngModel)]="versionConfig.downloadCount"
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
              />
            </div>
          </div>

          <div>
            <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">URL Directa de Descarga (.APK)</label>
            <div class="relative">
              <input
                type="url"
                [(ngModel)]="versionConfig.downloadUrl"
                placeholder="https://github.com/.../Audiocinemateca.apk"
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500 font-mono text-xs"
              />
            </div>
          </div>

          <div>
            <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Registro de Cambios (Changelog / Novedades)</label>
            <textarea
              [(ngModel)]="versionConfig.changelog"
              rows="5"
              placeholder="- Nueva función...\n- Corrección de errores..."
              class="w-full bg-slate-950 border border-slate-800 rounded-xl p-4 text-white text-sm focus:outline-none focus:border-indigo-500 font-mono text-xs leading-relaxed"
            ></textarea>
            <span class="text-[11px] text-slate-500">Este texto se presentará de forma accesible en el diálogo "Novedades" al iniciar la app.</span>
          </div>

          <div class="pt-4 border-t border-slate-800 flex items-center justify-between">
            <label class="flex items-center gap-3 cursor-pointer">
              <input
                type="checkbox"
                [(ngModel)]="versionConfig.forceUpdate"
                class="w-5 h-5 rounded bg-slate-950 border-slate-800 text-rose-600 focus:ring-0 focus:ring-offset-0 cursor-pointer"
              />
              <div>
                <span class="text-sm font-semibold text-white block">Forzar Actualización Obligatoria</span>
                <span class="text-xs text-rose-400">Si se activa, los usuarios no podrán saltarse la actualización ni usar la app sin actualizar.</span>
              </div>
            </label>

            <button
              (click)="saveVersionConfig()"
              [disabled]="isSaving()"
              class="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-sm font-semibold shadow-lg shadow-indigo-600/30 transition flex items-center gap-2 cursor-pointer"
            >
              @if (isSaving()) {
                <svg class="animate-spin w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
              }
              Guardar Configuración de Versión
            </button>
          </div>
        </div>
      }

      <!-- 2. TAB: BANNER CONFIG -->
      @if (activeTab() === 'banner') {
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-6">
          <div class="flex items-center justify-between border-b border-slate-800 pb-4">
            <div>
              <h3 class="text-lg font-bold text-white">Banner Global Destacado</h3>
              <p class="text-xs text-slate-400">Mensaje fijado en la parte superior del Feed principal de la aplicación para avisos masivos.</p>
            </div>
            <label class="relative inline-flex items-center cursor-pointer">
              <input type="checkbox" [(ngModel)]="bannerConfig.enabled" class="sr-only peer" />
              <div class="w-11 h-6 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-emerald-500"></div>
              <span class="ml-3 text-sm font-medium text-slate-300">
                {{ bannerConfig.enabled ? 'Banner Activado' : 'Banner Oculto' }}
              </span>
            </label>
          </div>

          <!-- BANNER PREVIEW -->
          <div class="p-4 rounded-xl border"
               [class.bg-blue-500/10]="bannerConfig.type === 'info'"
               [class.border-blue-500/30]="bannerConfig.type === 'info'"
               [class.bg-amber-500/10]="bannerConfig.type === 'warning'"
               [class.border-amber-500/30]="bannerConfig.type === 'warning'"
               [class.bg-rose-500/10]="bannerConfig.type === 'alert'"
               [class.border-rose-500/30]="bannerConfig.type === 'alert'"
               [class.bg-emerald-500/10]="bannerConfig.type === 'update'"
               [class.border-emerald-500/30]="bannerConfig.type === 'update'"
          >
            <div class="text-[11px] uppercase font-bold tracking-wider mb-2 text-slate-400">Previsualización en Pantalla</div>
            <div class="flex items-start justify-between gap-4">
              <div>
                <h4 class="font-bold text-white text-base">{{ bannerConfig.title || 'Título del Banner' }}</h4>
                <p class="text-sm text-slate-300 mt-1">{{ bannerConfig.message || 'Cuerpo del mensaje que verán los usuarios en su feed...' }}</p>
                @if (bannerConfig.actionText) {
                  <button class="mt-3 px-3 py-1 bg-white/10 hover:bg-white/20 text-white rounded-lg text-xs font-semibold transition">
                    {{ bannerConfig.actionText }}
                  </button>
                }
              </div>
              @if (bannerConfig.dismissible) {
                <span class="text-xs text-slate-400 border border-slate-700 px-2 py-0.5 rounded">Descartable</span>
              }
            </div>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">ID Único del Banner (Cambiar para volver a mostrar a quienes lo descartaron)</label>
              <input
                type="text"
                [(ngModel)]="bannerConfig.id"
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Tipo de Alerta Visual</label>
              <select
                [(ngModel)]="bannerConfig.type"
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
              >
                <option value="info">Información (Azul)</option>
                <option value="warning">Advertencia (Ámbar)</option>
                <option value="alert">Alerta Crítica (Rojo)</option>
                <option value="update">Actualización / Nueva Versión (Verde)</option>
              </select>
            </div>
          </div>

          <div>
            <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Título del Banner</label>
            <input
              type="text"
              [(ngModel)]="bannerConfig.title"
              class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
            />
          </div>

          <div>
            <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Mensaje Explicativo</label>
            <textarea
              [(ngModel)]="bannerConfig.message"
              rows="3"
              class="w-full bg-slate-950 border border-slate-800 rounded-xl p-4 text-white text-sm focus:outline-none focus:border-indigo-500 leading-relaxed"
            ></textarea>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Texto del Botón de Acción (Opcional)</label>
              <input
                type="text"
                [(ngModel)]="bannerConfig.actionText"
                placeholder="Ver más / Donar / Actualizar"
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">URL o Destino del Botón (Opcional)</label>
              <input
                type="text"
                [(ngModel)]="bannerConfig.actionUrl"
                placeholder="https://..."
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
              />
            </div>
          </div>

          <div class="pt-4 border-t border-slate-800 flex items-center justify-between">
            <label class="flex items-center gap-3 cursor-pointer">
              <input
                type="checkbox"
                [(ngModel)]="bannerConfig.dismissible"
                class="w-5 h-5 rounded bg-slate-950 border-slate-800 text-indigo-600 focus:ring-0 focus:ring-offset-0 cursor-pointer"
              />
              <span class="text-sm font-medium text-slate-300">Permitir que el usuario cierre o descarte este banner</span>
            </label>

            <button
              (click)="saveBannerConfig()"
              [disabled]="isSaving()"
              class="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-sm font-semibold shadow-lg shadow-indigo-600/30 transition flex items-center gap-2 cursor-pointer"
            >
              Guardar Banner
            </button>
          </div>
        </div>
      }

      <!-- 3. TAB: MAINTENANCE MODE -->
      @if (activeTab() === 'maintenance') {
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-6">
          <div class="flex items-center justify-between border-b border-slate-800 pb-4">
            <div>
              <h3 class="text-lg font-bold text-white">Modo Mantenimiento</h3>
              <p class="text-xs text-slate-400">Bloquea temporalmente el uso de la aplicación móvil y muestra una pantalla explicativa.</p>
            </div>
            <label class="relative inline-flex items-center cursor-pointer">
              <input type="checkbox" [(ngModel)]="maintenanceConfig.enabled" class="sr-only peer" />
              <div class="w-11 h-6 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-rose-500"></div>
              <span class="ml-3 text-sm font-medium text-slate-300">
                {{ maintenanceConfig.enabled ? 'Mantenimiento ACTIVO' : 'Sistema Operativo' }}
              </span>
            </label>
          </div>

          @if (maintenanceConfig.enabled) {
            <div class="p-4 bg-rose-500/10 border border-rose-500/30 rounded-xl text-rose-300 text-sm flex items-center gap-3">
              <svg class="w-6 h-6 flex-shrink-0 text-rose-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
              <span><strong>¡Atención!</strong> El modo mantenimiento está activo. Todos los usuarios de la app móvil verán la pantalla de mantenimiento y no podrán reproducir audio.</span>
            </div>
          }

          <div>
            <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Mensaje para los Usuarios</label>
            <textarea
              [(ngModel)]="maintenanceConfig.message"
              rows="4"
              class="w-full bg-slate-950 border border-slate-800 rounded-xl p-4 text-white text-sm focus:outline-none focus:border-indigo-500 leading-relaxed"
            ></textarea>
          </div>

          <div>
            <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Hora / Fecha Estimada de Retorno (Opcional)</label>
            <input
              type="text"
              [(ngModel)]="maintenanceConfig.estimatedEndTime"
              placeholder="Ej. Hoy a las 22:00 UTC / En unos minutos"
              class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
            />
          </div>

          <div class="pt-4 border-t border-slate-800 flex justify-end">
            <button
              (click)="saveMaintenanceConfig()"
              [disabled]="isSaving()"
              class="px-6 py-2.5 bg-rose-600 hover:bg-rose-500 text-white rounded-xl text-sm font-semibold shadow-lg shadow-rose-600/30 transition flex items-center gap-2 cursor-pointer"
            >
              Guardar Estado de Mantenimiento
            </button>
          </div>
        </div>
      }

      <!-- 4. TAB: CHAT & COMMUNITY -->
      @if (activeTab() === 'chat') {
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-6">
          <div class="flex items-center justify-between border-b border-slate-800 pb-4">
            <div>
              <h3 class="text-lg font-bold text-white">Parámetros del Chat Global</h3>
              <p class="text-xs text-slate-400">Límites contra spam, lentitud y bienvenida en el chat en tiempo real.</p>
            </div>
            <label class="relative inline-flex items-center cursor-pointer">
              <input type="checkbox" [(ngModel)]="chatConfig.enabled" class="sr-only peer" />
              <div class="w-11 h-6 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-emerald-500"></div>
              <span class="ml-3 text-sm font-medium text-slate-300">
                {{ chatConfig.enabled ? 'Chat Habilitado' : 'Chat Apagado' }}
              </span>
            </label>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Modo Lento / Enfriamiento (Segundos entre mensajes)</label>
              <input
                type="number"
                [(ngModel)]="chatConfig.slowModeSeconds"
                min="0"
                max="60"
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
              />
              <span class="text-[11px] text-slate-500">Evita que usuarios envíen ráfagas de spam seguidas.</span>
            </div>

            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Longitud Máxima por Mensaje (Caracteres)</label>
              <input
                type="number"
                [(ngModel)]="chatConfig.maxMessageLength"
                min="50"
                max="2000"
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
              />
              <span class="text-[11px] text-slate-500">Recomendado: 500 caracteres para TalkBack.</span>
            </div>
          </div>

          <div>
            <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Mensaje de Bienvenida / Reglas del Chat</label>
            <textarea
              [(ngModel)]="chatConfig.welcomeMessage"
              rows="3"
              class="w-full bg-slate-950 border border-slate-800 rounded-xl p-4 text-white text-sm focus:outline-none focus:border-indigo-500 leading-relaxed"
            ></textarea>
          </div>

          <div class="pt-4 border-t border-slate-800 flex justify-end">
            <button
              (click)="saveChatConfig()"
              [disabled]="isSaving()"
              class="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-sm font-semibold shadow-lg shadow-indigo-600/30 transition flex items-center gap-2 cursor-pointer"
            >
              Guardar Configuración de Chat
            </button>
          </div>
        </div>
      }

      <!-- 5. TAB: DONATIONS -->
      @if (activeTab() === 'donations') {
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-6">
          <div class="border-b border-slate-800 pb-4">
            <h3 class="text-lg font-bold text-white">Canales Oficiales de Donación y Soporte</h3>
            <p class="text-xs text-slate-400">Configura los enlaces que aparecen en los diálogos de soporte y botón "Invitame un café".</p>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Enlace PayPal Principal</label>
              <input
                type="url"
                [(ngModel)]="donationConfig.paypal"
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Enlace PayPal.Me ("Invitame un Café")</label>
              <input
                type="url"
                [(ngModel)]="donationConfig.paypalMe"
                class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
              />
            </div>
          </div>

          <div>
            <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">Correo de Soporte Oficial</label>
            <input
              type="email"
              [(ngModel)]="donationConfig.supportEmail"
              class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500"
            />
          </div>

          <div class="pt-4 border-t border-slate-800 flex justify-end">
            <button
              (click)="saveDonationsConfig()"
              [disabled]="isSaving()"
              class="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-sm font-semibold shadow-lg shadow-indigo-600/30 transition flex items-center gap-2 cursor-pointer"
            >
              Guardar Canales de Donación
            </button>
          </div>
        </div>
      }

      <!-- 6. TAB: RAW CONFIGS -->
      @if (activeTab() === 'raw') {
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-6">
          <div class="flex items-center justify-between border-b border-slate-800 pb-4">
            <div>
              <h3 class="text-lg font-bold text-white">Todas las Claves Almacenadas en PostgreSQL</h3>
              <p class="text-xs text-slate-400">Inspección directa de los registros de la tabla app_configs.</p>
            </div>
          </div>

          <div class="space-y-4">
            @for (item of rawConfigs(); track item.key) {
              <div class="p-4 bg-slate-950 border border-slate-800/80 rounded-xl space-y-2">
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-2">
                    <span class="font-mono text-sm font-bold text-indigo-400">{{ item.key }}</span>
                    @if (item.description) {
                      <span class="text-xs text-slate-400">• {{ item.description }}</span>
                    }
                  </div>
                  <span class="text-[11px] text-slate-500 font-mono">{{ item.updatedAt | date:'medium' }}</span>
                </div>
                <pre class="bg-slate-900/60 p-3 rounded-lg text-xs font-mono text-slate-300 overflow-x-auto max-h-48">{{ item.value | json }}</pre>
              </div>
            }
          </div>
        </div>
      }
    </div>
  `
})
export class ConfigEditorComponent implements OnInit {
  private readonly configService = inject(ConfigService);

  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly activeTab = signal<'version' | 'banner' | 'maintenance' | 'chat' | 'donations' | 'raw'>('version');
  readonly statusMessage = signal<string | null>(null);
  readonly statusType = signal<'success' | 'error'>('success');
  readonly rawConfigs = signal<AppConfigItem[]>([]);

  versionConfig: VersionConfig = {
    version: '3.1.0',
    versionCode: 31,
    name: 'Audiocinemateca v3.1.0',
    changelog: '',
    downloadUrl: '',
    minRequiredVersion: '3.0.0',
    forceUpdate: false,
    updatedAt: new Date().toISOString(),
    downloadCount: 0
  };

  bannerConfig: BannerConfig = {
    enabled: true,
    id: 'welcome_v3',
    title: '¡Bienvenido a Audiocinemateca!',
    message: '',
    type: 'info',
    dismissible: true,
    actionUrl: null,
    actionText: null
  };

  maintenanceConfig: MaintenanceConfig = {
    enabled: false,
    message: 'Audiocinemateca se encuentra en mantenimiento programado. Volveremos muy pronto.',
    estimatedEndTime: null
  };

  chatConfig: ChatConfig = {
    enabled: true,
    slowModeSeconds: 3,
    maxMessageLength: 500,
    welcomeMessage: ''
  };

  donationConfig: DonationConfig = {
    paypal: '',
    paypalMe: '',
    supportEmail: ''
  };

  ngOnInit() {
    this.loadConfigs();
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
      error: (err) => {
        this.isLoading.set(false);
        this.showStatus('Error al cargar configuraciones remotas', 'error');
      }
    });
  }

  saveVersionConfig() {
    this.isSaving.set(true);
    this.versionConfig.updatedAt = new Date().toISOString();
    this.configService.updateConfig('app_version', this.versionConfig, 'Información y control de versión para el actualizador de la app').subscribe({
      next: () => {
        this.isSaving.set(false);
        this.showStatus('Configuración de versión guardada con éxito', 'success');
        this.loadConfigs();
      },
      error: () => {
        this.isSaving.set(false);
        this.showStatus('Error al guardar la configuración de versión', 'error');
      }
    });
  }

  saveBannerConfig() {
    this.isSaving.set(true);
    this.configService.updateConfig('featured_banner', this.bannerConfig, 'Banner superior global para comunicados en el feed').subscribe({
      next: () => {
        this.isSaving.set(false);
        this.showStatus('Banner global guardado y publicado', 'success');
        this.loadConfigs();
      },
      error: () => {
        this.isSaving.set(false);
        this.showStatus('Error al guardar el banner', 'error');
      }
    });
  }

  saveMaintenanceConfig() {
    this.isSaving.set(true);
    this.configService.updateConfig('maintenance_mode', this.maintenanceConfig, 'Estado global de modo mantenimiento').subscribe({
      next: () => {
        this.isSaving.set(false);
        this.showStatus(this.maintenanceConfig.enabled ? '¡Modo mantenimiento ACTIVADO!' : 'Modo mantenimiento DESACTIVADO', 'success');
        this.loadConfigs();
      },
      error: () => {
        this.isSaving.set(false);
        this.showStatus('Error al cambiar modo de mantenimiento', 'error');
      }
    });
  }

  saveChatConfig() {
    this.isSaving.set(true);
    this.configService.updateConfig('chat_config', this.chatConfig, 'Parámetros de operación y límites del chat global').subscribe({
      next: () => {
        this.isSaving.set(false);
        this.showStatus('Configuración del chat actualizada', 'success');
        this.loadConfigs();
      },
      error: () => {
        this.isSaving.set(false);
        this.showStatus('Error al actualizar chat', 'error');
      }
    });
  }

  saveDonationsConfig() {
    this.isSaving.set(true);
    this.configService.updateConfig('donation_links', this.donationConfig, 'Enlaces oficiales de donación y soporte del proyecto').subscribe({
      next: () => {
        this.isSaving.set(false);
        this.showStatus('Canales de donación actualizados', 'success');
        this.loadConfigs();
      },
      error: () => {
        this.isSaving.set(false);
        this.showStatus('Error al guardar enlaces de donación', 'error');
      }
    });
  }

  seedDefaults() {
    if (!confirm('¿Deseas restaurar o re-sembrar las configuraciones por defecto?')) return;
    this.isSaving.set(true);
    this.configService.seedDefaults().subscribe({
      next: () => {
        this.isSaving.set(false);
        this.showStatus('Valores por defecto restablecidos con éxito', 'success');
        this.loadConfigs();
      },
      error: () => {
        this.isSaving.set(false);
        this.showStatus('Error al restaurar configuraciones', 'error');
      }
    });
  }

  private showStatus(msg: string, type: 'success' | 'error') {
    this.statusMessage.set(msg);
    this.statusType.set(type);
    setTimeout(() => {
      this.statusMessage.set(null);
    }, 4000);
  }
}
