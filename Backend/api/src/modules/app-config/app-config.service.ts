import { Injectable, Logger, OnModuleInit, NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PrismaService } from '../../prisma/prisma.service.js';
import { CatalogSyncService } from '../catalog-sync/catalog-sync.service.js';
import { AuraAiService } from '../ai/aura-ai.service.js';
import { PushService } from '../notifications/push.service.js';
import * as https from 'https';

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
  description?: string;
  type: 'info' | 'warning' | 'alert' | 'update';
  dismissible: boolean;
  actionUrl: string | null;
  actionText: string | null;
  linkUrl?: string | null;
  itemId?: string | null;
  itemType?: string | null;
  genres?: string[];
  rating?: number;
  maxRating?: number;
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

export interface GithubReleaseItem {
  id: number;
  tagName: string;
  version: string;
  name: string;
  changelog: string;
  publishedAt: string;
  updatedAt: string;
  htmlUrl: string;
  prerelease: boolean;
  author: string;
  apkAsset: {
    name: string;
    sizeBytes: number;
    sizeMb: string;
    downloadCount: number;
    browserDownloadUrl: string;
  } | null;
}

@Injectable()
export class AppConfigService implements OnModuleInit {
  private readonly logger = new Logger(AppConfigService.name);
  private readonly cache = new Map<string, string>();
  private githubCache: { fetchedAt: number; data: any } | null = null;

  constructor(
    private readonly prisma: PrismaService,
    private readonly configService: ConfigService,
    private readonly catalogSyncService: CatalogSyncService,
    private readonly auraAiService: AuraAiService,
    private readonly pushService: PushService,
  ) {}

  async onModuleInit() {
    await this.seedDefaultConfigs();
  }

  // --- 1. CONSULTA EN TIEMPO REAL A GITHUB RELEASES (OFICIAL APK) ---
  async getGithubReleases(forceRefresh = false) {
    const CACHE_TTL_MS = 2 * 60 * 1000; // 2 minutos de caché para velocidad y evitar límite de GitHub
    if (!forceRefresh && this.githubCache && Date.now() - this.githubCache.fetchedAt < CACHE_TTL_MS) {
      return this.githubCache.data;
    }

    const repo = (this.configService.get<string>('GITHUB_REPO') || 'JohanAnim/Audiocinemateca').replace(
      /^["']|["']$/g,
      '',
    );

    try {
      const rawReleases = await this.fetchGithubJson(`https://api.github.com/repos/${repo}/releases`);
      const list = Array.isArray(rawReleases) ? rawReleases : [];

      let totalDownloadsAllTime = 0;

      const releases: GithubReleaseItem[] = list.map((rel: any) => {
        const assets = Array.isArray(rel.assets) ? rel.assets : [];
        const apk = assets.find((a: any) => String(a.name || '').toLowerCase().endsWith('.apk')) || assets[0] || null;

        let apkAsset = null;
        if (apk) {
          const dlCount = Number(apk.download_count || 0);
          totalDownloadsAllTime += dlCount;
          const sizeBytes = Number(apk.size || 0);
          apkAsset = {
            name: apk.name,
            sizeBytes,
            sizeMb: (sizeBytes / (1024 * 1024)).toFixed(2) + ' MB',
            downloadCount: dlCount,
            browserDownloadUrl: apk.browser_download_url,
          };
        }

        const tag = String(rel.tag_name || '');
        return {
          id: rel.id,
          tagName: tag,
          version: tag.replace(/^v/i, ''),
          name: rel.name || tag,
          changelog: rel.body || 'Sin notas de versión especificadas.',
          publishedAt: rel.published_at || rel.created_at,
          updatedAt: apk?.updated_at || rel.published_at || rel.created_at,
          htmlUrl: rel.html_url,
          prerelease: Boolean(rel.prerelease),
          author: rel.author?.login || 'JohanAnim',
          apkAsset,
        };
      });

      const latestRelease = releases.find((r) => !r.prerelease) || releases[0] || null;

      // Sincronizar versión con app_version en segundo plano si existe latestRelease
      if (latestRelease && latestRelease.apkAsset) {
        const storedVersion = (await this.getParsedConfig<VersionInfo>('app_version')) || ({} as VersionInfo);
        const syncedVersion: VersionInfo = {
          version: latestRelease.version || latestRelease.tagName,
          versionCode: storedVersion.versionCode || 31,
          name: latestRelease.name,
          changelog: latestRelease.changelog,
          downloadUrl: latestRelease.apkAsset.browserDownloadUrl,
          minRequiredVersion: storedVersion.minRequiredVersion || '3.0.0',
          forceUpdate: Boolean(storedVersion.forceUpdate),
          updatedAt: latestRelease.updatedAt,
          downloadCount: latestRelease.apkAsset.downloadCount,
        };
        await this.upsertConfig('app_version', syncedVersion, 'Sincronizado desde GitHub Releases');
      }

      const result = {
        repository: repo,
        repositoryUrl: `https://github.com/${repo}`,
        releasesUrl: `https://github.com/${repo}/releases`,
        fetchedAt: new Date().toISOString(),
        totalReleases: releases.length,
        totalDownloadsAllTime,
        latestRelease,
        releases,
      };

      this.githubCache = { fetchedAt: Date.now(), data: result };
      return result;
    } catch (err: any) {
      this.logger.warn(`⚠️ Error consultando GitHub API (${repo}): ${err.message}`);
      const fallbackVersion = await this.getParsedConfig<VersionInfo>('app_version');
      return {
        repository: repo,
        repositoryUrl: `https://github.com/${repo}`,
        releasesUrl: `https://github.com/${repo}/releases`,
        fetchedAt: new Date().toISOString(),
        error: err.message,
        totalReleases: fallbackVersion ? 1 : 0,
        totalDownloadsAllTime: fallbackVersion?.downloadCount || 0,
        latestRelease: fallbackVersion
          ? {
              id: 0,
              tagName: fallbackVersion.version,
              version: fallbackVersion.version,
              name: fallbackVersion.name,
              changelog: fallbackVersion.changelog,
              publishedAt: fallbackVersion.updatedAt,
              updatedAt: fallbackVersion.updatedAt,
              htmlUrl: `https://github.com/${repo}/releases`,
              prerelease: false,
              author: 'JohanAnim',
              apkAsset: {
                name: `Audiocinemateca-${fallbackVersion.version}.apk`,
                sizeBytes: 0,
                sizeMb: 'N/A',
                downloadCount: fallbackVersion.downloadCount || 0,
                browserDownloadUrl: fallbackVersion.downloadUrl,
              },
            }
          : null,
        releases: [],
      };
    }
  }

  private fetchGithubJson(url: string): Promise<any> {
    return new Promise((resolve, reject) => {
      const req = https.get(
        url,
        {
          headers: {
            'User-Agent': 'Audiocinemateca-ControlPanel/3.1',
            Accept: 'application/vnd.github+json',
          },
          timeout: 12000,
        },
        (res) => {
          let body = '';
          res.on('data', (chunk) => (body += chunk));
          res.on('end', () => {
            if (res.statusCode && res.statusCode >= 200 && res.statusCode < 300) {
              try {
                resolve(JSON.parse(body));
              } catch (e) {
                reject(e);
              }
            } else {
              reject(new Error(`GitHub API HTTP ${res.statusCode}`));
            }
          });
        },
      );
      req.on('timeout', () => req.destroy(new Error('Timeout consultando GitHub API')));
      req.on('error', reject);
    });
  }

  // --- 2. PUBLICAR BANNER DESTACADO CON AUTODETECCIÓN DE OBRA Y PUSH IA (MIGRADO DE bannerModule.js) ---
  async publishFeaturedBanner(input: {
    enabled?: boolean;
    id?: string;
    title: string;
    message: string;
    type?: 'info' | 'warning' | 'alert' | 'update';
    dismissible?: boolean;
    actionUrl?: string | null;
    actionText?: string | null;
    sendPush?: boolean;
    optimizePushWithAi?: boolean;
  }) {
    const linkUrl = (input.actionUrl || '').trim();
    let finalItemId = '';
    let finalItemType = 'pelicula';
    let finalGenres = ['Audio'];
    let officialRating = 0.0;
    let matchedTitle: string | null = null;

    if (linkUrl) {
      const found = this.catalogSyncService.findItemInCatalog(linkUrl);
      if (found) {
        finalItemId = found.itemId;
        finalItemType = found.itemType;
        finalGenres = found.genres;
        matchedTitle = found.title;

        // Buscar calificación oficial en PostgreSQL o usar Filmaffinity
        const dbContent = await this.prisma.content.findFirst({
          where: {
            OR: [
              { externalId: found.itemId },
              { externalId: `movie_${found.itemId}` },
              { externalId: `series_${found.itemId}` },
              { externalId: `doc_${found.itemId}` },
              { externalId: `short_${found.itemId}` },
            ],
          },
          select: { averageRating: true, ratingsCount: true },
        });

        if (dbContent && dbContent.ratingsCount > 0 && dbContent.averageRating > 0) {
          officialRating = Number(dbContent.averageRating.toFixed(1));
        } else if (found.filmaffinity) {
          const clean = String(found.filmaffinity).trim().replace(',', '.');
          const num = parseFloat(clean);
          if (!isNaN(num) && num > 0) {
            officialRating = Number((num > 5.0 ? num / 2.0 : num).toFixed(1));
          }
        }
      } else {
        finalItemId = this.catalogSyncService.parseUrlIdAndHint(linkUrl).itemId || 'general';
      }
    }

    const bannerPayload: BannerInfo = {
      enabled: input.enabled ?? true,
      id: input.id || `banner_${Date.now()}`,
      title: input.title.trim(),
      message: input.message.trim(),
      description: input.message.trim(),
      type: input.type || 'info',
      dismissible: input.dismissible ?? true,
      actionUrl: linkUrl || null,
      linkUrl: linkUrl || null,
      actionText: input.actionText?.trim() || (linkUrl ? 'Escuchar ahora' : null),
      itemId: finalItemId,
      itemType: finalItemType,
      genres: finalGenres,
      rating: officialRating,
      maxRating: 5,
    };

    await this.upsertConfig(
      'featured_banner',
      bannerPayload,
      'Banner superior global para comunicados o estrenos destacados en el feed',
    );

    let pushResult: any = null;
    if (input.sendPush) {
      let pushTitle = `🎬 ${bannerPayload.title.substring(0, 45)}`;
      let pushBody = bannerPayload.message.replace(/\n/g, ' ').substring(0, 140);

      if (input.optimizePushWithAi !== false) {
        const aiCopy = await this.auraAiService.generatePushCopyWithAi(
          bannerPayload.title,
          bannerPayload.message,
        );
        pushTitle = aiCopy.pushTitle;
        pushBody = aiCopy.pushBody;
      }

      pushResult = await this.pushService.sendGlobalPush({
        title: pushTitle,
        body: pushBody,
        destination: finalItemId && finalItemId !== 'general' ? 'content_detail' : linkUrl ? 'external_url' : 'app',
        contentType: finalItemType,
        contentId: finalItemId,
        url: linkUrl || undefined,
      });
    }

    return {
      success: true,
      banner: bannerPayload,
      matchedCatalogWork: matchedTitle,
      pushResult,
    };
  }

  // --- 3. OBTENER TODAS LAS CONFIGURACIONES (ADMIN) ---
  async getAllConfigs() {
    const configs = await this.prisma.appConfig.findMany({
      where: {
        key: {
          notIn: ['aura_brain_secrets', 'gemini_api_key'],
        },
      },
      orderBy: { key: 'asc' },
    });

    return configs.map((c) => ({
      key: c.key,
      value: this.tryParseJson(c.value),
      description: c.description,
      updatedAt: c.updatedAt,
    }));
  }

  // --- 4. OBTENER CONFIGURACIÓN PÚBLICA CONSOLIDADA (APP CLIENT) ---
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

  // --- 5. COMPROBACIÓN DE ACTUALIZACIONES (SINCRONIZADO CON GITHUB RELEASES) ---
  async checkVersion(clientVersion?: string) {
    // Consultar GitHub (con caché de 2 min) para tener siempre los datos reales de la última release
    const ghData = await this.getGithubReleases(false);
    const versionConfig = await this.getParsedConfig<VersionInfo>('app_version');

    if (!versionConfig && !ghData?.latestRelease) {
      throw new NotFoundException('Configuración de versión no encontrada');
    }

    const latestVer = ghData?.latestRelease?.version || versionConfig?.version || '3.1.0';
    const currentClient = clientVersion ? clientVersion.trim() : '0.0.0';
    const isUpdateAvailable = this.compareVersions(currentClient, latestVer) < 0;
    const minReq = versionConfig?.minRequiredVersion || '3.0.0';
    const isForceUpdate =
      Boolean(versionConfig?.forceUpdate) || this.compareVersions(currentClient, minReq) < 0;

    return {
      version: latestVer,
      versionCode: versionConfig?.versionCode || 31,
      name: ghData?.latestRelease?.name || versionConfig?.name || `Audiocinemateca v${latestVer}`,
      changelog: ghData?.latestRelease?.changelog || versionConfig?.changelog || '',
      downloadUrl:
        ghData?.latestRelease?.apkAsset?.browserDownloadUrl || versionConfig?.downloadUrl || '',
      updatedAt: ghData?.latestRelease?.updatedAt || versionConfig?.updatedAt || new Date().toISOString(),
      downloadCount:
        ghData?.latestRelease?.apkAsset?.downloadCount ?? versionConfig?.downloadCount ?? 0,
      minRequiredVersion: minReq,
      isUpdateAvailable,
      isForceUpdate,
      clientVersion: currentClient,
    };
  }

  // --- 6. OBTENER BANNER ACTIVO ---
  async getActiveBanner() {
    const banner = await this.getParsedConfig<BannerInfo>('featured_banner');
    if (!banner || !banner.enabled) {
      return null;
    }
    return banner;
  }

  // --- 7. OBTENER VALOR POR CLAVE ---
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

  // --- 8. GUARDAR O ACTUALIZAR CONFIGURACIÓN (ADMIN) ---
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

    this.cache.set(key, stringValue);
    this.logger.log(`⚙️ Configuración actualizada: "${key}"`);

    return {
      key: updated.key,
      value: this.tryParseJson(updated.value),
      description: updated.description,
      updatedAt: updated.updatedAt,
    };
  }

  // --- 9. ELIMINAR CONFIGURACIÓN (ADMIN) ---
  async deleteConfig(key: string) {
    await this.prisma.appConfig.delete({
      where: { key },
    });

    this.cache.delete(key);
    this.logger.log(`🗑️ Configuración eliminada: "${key}"`);

    return { success: true, message: `Configuración ${key} eliminada correctamente` };
  }

  // --- 10. INICIALIZADOR DE CONFIGURACIONES POR DEFECTO ---
  async seedDefaultConfigs() {
    const defaults: Record<string, { value: any; description: string }> = {
      app_version: {
        value: {
          version: '3.0.0',
          versionCode: 30,
          name: 'Audiocinemateca v3.0.0',
          changelog:
            '- Hito Aura 2.0 y sistema TTS independiente por motor\n- Chat global en tiempo real y anuncios accesibles\n- Blindaje de Google Cast en segundo plano',
          downloadUrl:
            'https://github.com/JohanAnim/Audiocinemateca/releases/latest',
          minRequiredVersion: '3.0.0',
          forceUpdate: false,
          updatedAt: new Date().toISOString(),
          downloadCount: 0,
        },
        description: 'Información de versión sincronizada automáticamente con GitHub Releases',
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
          message: 'Disfruta de miles de películas, series, cortos y documentales con audiodescripción.',
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
          paypal: 'https://www.paypal.com/donate/?hosted_button_id=T4H2LCSZDRV8J',
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

  private tryParseJson(value: string): any {
    try {
      return JSON.parse(value);
    } catch {
      return value;
    }
  }

  private compareVersions(v1: string, v2: string): number {
    const clean1 = v1.replace(/^v/i, '').split('.').map((n) => parseInt(n, 10) || 0);
    const clean2 = v2.replace(/^v/i, '').split('.').map((n) => parseInt(n, 10) || 0);
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
