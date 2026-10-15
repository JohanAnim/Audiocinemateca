import { Injectable, Logger, OnModuleInit, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service.js';

export interface VersionInfo {
  version: string;
  versionCode: number;
  name: string;
  changelog: string;
  downloadUrl: string;
  minRequiredVersion: string;
  forceUpdate: boolean;
  updatedAt: string;
  downloadCount: number;
}

export interface MaintenanceInfo {
  enabled: boolean;
  message: string;
  estimatedEndTime: string | null;
}

export interface BannerInfo {
  enabled: boolean;
  id: string;
  title: string;
  message: string;
  type: 'info' | 'warning' | 'alert' | 'update';
  dismissible: boolean;
  actionUrl: string | null;
  actionText: string | null;
}

export interface ChatConfigInfo {
  enabled: boolean;
  slowModeSeconds: number;
  maxMessageLength: number;
  welcomeMessage: string;
}

export interface DonationLinksInfo {
  paypal: string;
  paypalMe: string;
  supportEmail: string;
}

@Injectable()
export class AppConfigService implements OnModuleInit {
  private readonly logger = new Logger(AppConfigService.name);
  private readonly cache = new Map<string, string>();

  constructor(private readonly prisma: PrismaService) {}

  async onModuleInit() {
    await this.seedDefaultConfigs();
  }

  // --- 1. OBTENER TODAS LAS CONFIGURACIONES (ADMIN) ---
  async getAllConfigs() {
    const configs = await this.prisma.appConfig.findMany({
      orderBy: { key: 'asc' },
    });

    return configs.map((c) => ({
      key: c.key,
      value: this.tryParseJson(c.value),
      description: c.description,
      updatedAt: c.updatedAt,
    }));
  }

  // --- 2. OBTENER CONFIGURACIÓN PÚBLICA CONSOLIDADA (APP CLIENT) ---
  async getPublicAppConfig() {
    const version = await this.getParsedConfig<VersionInfo>('app_version');
    const maintenance = await this.getParsedConfig<MaintenanceInfo>('maintenance_mode');
    const banner = await this.getParsedConfig<BannerInfo>('featured_banner');
    const chat = await this.getParsedConfig<ChatConfigInfo>('chat_config');
    const donations = await this.getParsedConfig<DonationLinksInfo>('donation_links');

    return {
      version: version || null,
      maintenance: maintenance || { enabled: false, message: '', estimatedEndTime: null },
      banner: banner || null,
      chat: chat || { enabled: true, slowModeSeconds: 3, maxMessageLength: 500, welcomeMessage: '' },
      donations: donations || null,
      offlineSupport: true,
      serverTime: new Date().toISOString(),
    };
  }

  // --- 3. COMPROBACIÓN DE ACTUALIZACIONES (COMPATIBLE CON UpdateInfo DE ANDROID) ---
  async checkVersion(clientVersion?: string) {
    const versionConfig = await this.getParsedConfig<VersionInfo>('app_version');

    if (!versionConfig) {
      throw new NotFoundException('Configuración de versión no encontrada en el sistema');
    }

    const currentClient = clientVersion ? clientVersion.trim() : '0.0.0';
    const isUpdateAvailable = this.compareVersions(currentClient, versionConfig.version) < 0;
    const isForceUpdate =
      versionConfig.forceUpdate ||
      this.compareVersions(currentClient, versionConfig.minRequiredVersion) < 0;

    return {
      version: versionConfig.version,
      versionCode: versionConfig.versionCode,
      name: versionConfig.name,
      changelog: versionConfig.changelog,
      downloadUrl: versionConfig.downloadUrl,
      updatedAt: versionConfig.updatedAt,
      downloadCount: versionConfig.downloadCount,
      minRequiredVersion: versionConfig.minRequiredVersion,
      isUpdateAvailable,
      isForceUpdate,
      clientVersion: currentClient,
    };
  }

  // --- 4. OBTENER BANNER ACTIVO ---
  async getActiveBanner() {
    const banner = await this.getParsedConfig<BannerInfo>('featured_banner');
    if (!banner || !banner.enabled) {
      return null;
    }
    return banner;
  }

  // --- 5. OBTENER VALOR POR CLAVE ---
  async getConfig(key: string): Promise<string | null> {
    if (this.cache.has(key)) {
      return this.cache.get(key) || null;
    }

    const config = await this.prisma.appConfig.findUnique({
      where: { key },
    });

    if (config) {
      this.cache.set(key, config.value);
      return config.value;
    }

    return null;
  }

  async getParsedConfig<T>(key: string): Promise<T | null> {
    const raw = await this.getConfig(key);
    if (!raw) return null;
    return this.tryParseJson(raw) as T;
  }

  // --- 6. GUARDAR O ACTUALIZAR CONFIGURACIÓN (ADMIN) ---
  async upsertConfig(key: string, value: any, description?: string) {
    const stringValue = typeof value === 'string' ? value : JSON.stringify(value);

    const updated = await this.prisma.appConfig.upsert({
      where: { key },
      update: {
        value: stringValue,
        description: description !== undefined ? description : undefined,
      },
      create: {
        key,
        value: stringValue,
        description,
      },
    });

    // Actualizar caché en memoria
    this.cache.set(key, stringValue);
    this.logger.log(`⚙️ Configuración actualizada: "${key}"`);

    return {
      key: updated.key,
      value: this.tryParseJson(updated.value),
      description: updated.description,
      updatedAt: updated.updatedAt,
    };
  }

  // --- 7. ELIMINAR CONFIGURACIÓN (ADMIN) ---
  async deleteConfig(key: string) {
    await this.prisma.appConfig.delete({
      where: { key },
    });

    this.cache.delete(key);
    this.logger.log(`🗑️ Configuración eliminada: "${key}"`);

    return { success: true, message: `Configuración ${key} eliminada correctamente` };
  }

  // --- 8. INICIALIZADOR DE CONFIGURACIONES POR DEFECTO ---
  async seedDefaultConfigs() {
    const defaults: Record<string, { value: any; description: string }> = {
      app_version: {
        value: {
          version: '3.1.0',
          versionCode: 31,
          name: 'Audiocinemateca v3.1.0 (Backend Propio)',
          changelog:
            '- Migración completa a backend independiente de alto rendimiento\n- Chat global en tiempo real ultra rápido con WebSockets\n- Sincronización instantánea de progreso y favoritos en la nube\n- Mejoras de estabilidad en reproducción y descargas',
          downloadUrl:
            'https://github.com/gutierrezjohan/Audiocinemateca/releases/download/v3.1.0/Audiocinemateca-v3.1.0.apk',
          minRequiredVersion: '3.0.0',
          forceUpdate: false,
          updatedAt: new Date().toISOString(),
          downloadCount: 1250,
        },
        description: 'Información y control de versión para el actualizador de la app',
      },
      maintenance_mode: {
        value: {
          enabled: false,
          message: 'Audiocinemateca se encuentra en mantenimiento programado. Volveremos muy pronto.',
          estimatedEndTime: null,
        },
        description: 'Estado global de modo mantenimiento para bloquear acceso temporalmente',
      },
      featured_banner: {
        value: {
          enabled: true,
          id: 'welcome_v3',
          title: '¡Bienvenido a Audiocinemateca!',
          message: 'Disfruta de cientos de películas y series con audiodescripción en español.',
          type: 'info',
          dismissible: true,
          actionUrl: null,
          actionText: null,
        },
        description: 'Banner superior global para comunicados o anuncios en el feed',
      },
      chat_config: {
        value: {
          enabled: true,
          slowModeSeconds: 3,
          maxMessageLength: 500,
          welcomeMessage: '¡Hola! Respeta las normas de la comunidad para una convivencia accesible y amena.',
        },
        description: 'Parámetros de operación y límites del chat global',
      },
      donation_links: {
        value: {
          paypal: 'https://paypal.me/audiocinemateca',
          paypalMe: 'https://paypal.me/audiocinemateca',
          supportEmail: 'soporte@audiocinemateca.com',
        },
        description: 'Enlaces oficiales de donación y soporte del proyecto',
      },
    };

    for (const [key, item] of Object.entries(defaults)) {
      const existing = await this.prisma.appConfig.findUnique({ where: { key } });
      if (!existing) {
        await this.prisma.appConfig.create({
          data: {
            key,
            value: JSON.stringify(item.value),
            description: item.description,
          },
        });
        this.cache.set(key, JSON.stringify(item.value));
        this.logger.log(`🌱 Configuración por defecto creada: "${key}"`);
      } else {
        this.cache.set(key, existing.value);
      }
    }
  }

  // --- UTILIDADES INTERNAS ---
  private tryParseJson(value: string): any {
    try {
      return JSON.parse(value);
    } catch {
      return value;
    }
  }

  private compareVersions(v1: string, v2: string): number {
    const clean1 = v1.replace(/^v/, '').split('.').map((n) => parseInt(n, 10) || 0);
    const clean2 = v2.replace(/^v/, '').split('.').map((n) => parseInt(n, 10) || 0);
    const maxLen = Math.max(clean1.length, clean2.length);

    for (let i = 0; i < maxLen; i++) {
      const num1 = clean1[i] ?? 0;
      const num2 = clean2[i] ?? 0;
      if (num1 > num2) return 1;
      if (num1 < num2) return -1;
    }

    return 0;
  }
}
