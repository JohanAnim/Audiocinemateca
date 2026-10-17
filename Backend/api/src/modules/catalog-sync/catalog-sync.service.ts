import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PrismaService } from '../../prisma/prisma.service.js';
import { PushService } from '../notifications/push.service.js';
import { AuraAiService } from '../ai/aura-ai.service.js';
import { ContentType } from '@prisma/client';
import * as https from 'https';
import * as http from 'http';
import * as zlib from 'zlib';
import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const VERSION_URL = 'https://audiocinemateca.com/system/files/catalogo/version.json.gz';
const CATALOG_URL = 'https://audiocinemateca.com/system/files/catalogo/catalogo.json.gz';
const CHECK_INTERVAL_MS = 15 * 60 * 1000; // Cada 15 minutos

export interface SyncProgressState {
  isSyncing: boolean;
  stage: string;
  processed: number;
  total: number;
  percent: number;
  addedMovies: number;
  addedSeries: number;
  addedDocs: number;
  addedShorts: number;
  startedAt: string | null;
  completedAt: string | null;
  error: string | null;
}

@Injectable()
export class CatalogSyncService implements OnModuleInit {
  private readonly logger = new Logger(CatalogSyncService.name);
  private cachedVersionDate: string | null = null;
  private lastCheckedAt: string | null = null;

  private syncProgress: SyncProgressState = {
    isSyncing: false,
    stage: 'Inactivo',
    processed: 0,
    total: 0,
    percent: 0,
    addedMovies: 0,
    addedSeries: 0,
    addedDocs: 0,
    addedShorts: 0,
    startedAt: null,
    completedAt: null,
    error: null,
  };

  constructor(
    private readonly configService: ConfigService,
    private readonly prisma: PrismaService,
    private readonly pushService: PushService,
    private readonly auraAiService: AuraAiService,
  ) {}

  async onModuleInit() {
    this.logger.log('📦 [Servicio Catálogo] Monitoreo automático de actualizaciones activado (cada 15 min).');
    setTimeout(() => {
      this.checkCatalogUpdatesAutomatic(false).catch((e) =>
        this.logger.warn(`Aviso en chequeo inicial de catálogo: ${e.message}`),
      );
    }, 6000);

    setInterval(() => {
      this.checkCatalogUpdatesAutomatic(false).catch((e) =>
        this.logger.warn(`Aviso en chequeo periódico de catálogo: ${e.message}`),
      );
    }, CHECK_INTERVAL_MS);
  }

  private getDataDir(): string {
    const dir = path.resolve(process.cwd(), 'data');
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    return dir;
  }

  private getCatalogFilePath(): string {
    const primary = path.join(this.getDataDir(), 'catalogo.json');
    if (fs.existsSync(primary)) return primary;
    const fallback = path.resolve(__dirname, '../../../data/catalogo.json');
    if (fs.existsSync(fallback)) return fallback;
    return primary;
  }

  private getVersionFilePath(): string {
    const primary = path.join(this.getDataDir(), 'version.json');
    if (fs.existsSync(primary)) return primary;
    const fallback = path.resolve(__dirname, '../../../data/version.json');
    if (fs.existsSync(fallback)) return fallback;
    return primary;
  }

  public getAuthHeader(): string {
    const user = (this.configService.get<string>('CATALOG_USER') || 'Johan-a-g').replace(/^["']|["']$/g, '');
    const pass = (this.configService.get<string>('CATALOG_PASS') || '3tmgs8qd').replace(/^["']|["']$/g, '');
    return 'Basic ' + Buffer.from(`${user}:${pass}`).toString('base64');
  }

  public fetchGzipJson(url: string): Promise<any> {
    return new Promise((resolve, reject) => {
      const authHeader = this.getAuthHeader();
      const req = https.get(
        url,
        {
          headers: {
            Authorization: authHeader,
            'User-Agent': 'Audiocinemateca-Backend/3.1',
          },
          timeout: 25000,
        },
        (res) => {
          if (res.statusCode !== 200) {
            return reject(new Error(`HTTP status ${res.statusCode}`));
          }
          const chunks: Buffer[] = [];
          res.on('data', (chunk) => chunks.push(chunk));
          res.on('end', () => {
            const buffer = Buffer.concat(chunks);
            zlib.gunzip(buffer, (err, decompressed) => {
              if (err) {
                // Intentar parsear como JSON plano por si no vino comprimido
                try {
                  return resolve(JSON.parse(buffer.toString('utf-8')));
                } catch {
                  return reject(err);
                }
              }
              try {
                const cleanString = decompressed.toString('utf-8').trim();
                const jsonMatch = cleanString.match(/\{[\s\S]*\}/);
                const json = JSON.parse(jsonMatch ? jsonMatch[0] : cleanString);
                resolve(json);
              } catch (pErr) {
                reject(pErr);
              }
            });
          });
        },
      );

      req.on('timeout', () => {
        req.destroy(new Error('Tiempo de espera agotado al conectar con audiocinemateca.com'));
      });
      req.on('error', reject);
    });
  }

  public extractVersionString(versionData: any): string {
    if (!versionData) return '';
    if (typeof versionData === 'object') {
      if (versionData.year && versionData.mon && versionData.mday) {
        const pad = (n: any) => String(n || 0).padStart(2, '0');
        const h = pad(versionData.hours);
        const m = pad(versionData.minutes);
        const s = pad(versionData.seconds);
        return `${versionData.year}-${pad(versionData.mon)}-${pad(versionData.mday)}_${h}:${m}:${s}`;
      }
      if (versionData['0']) return String(versionData['0']);
      if (versionData.date) return String(versionData.date);
      if (versionData.version) return String(versionData.version);
      if (versionData.lastUpdated) return String(versionData.lastUpdated);
      return JSON.stringify(versionData);
    }
    return String(versionData);
  }

  public formatReadableVersion(versionData: any): string {
    if (!versionData) return 'Desconocida';
    if (typeof versionData === 'object' && versionData.year && versionData.mon && versionData.mday) {
      const pad = (n: any) => String(n || 0).padStart(2, '0');
      return `${pad(versionData.mday)}/${pad(versionData.mon)}/${versionData.year} ${pad(versionData.hours)}:${pad(versionData.minutes)}:${pad(versionData.seconds)}`;
    }
    return this.extractVersionString(versionData);
  }

  public getLocalCatalog(): any | null {
    try {
      const p = this.getCatalogFilePath();
      if (fs.existsSync(p)) {
        return JSON.parse(fs.readFileSync(p, 'utf-8'));
      }
    } catch (e: any) {
      this.logger.warn(`Error leyendo catalogo.json local: ${e.message}`);
    }
    return null;
  }

  public getLocalVersion(): any | null {
    try {
      const p = this.getVersionFilePath();
      if (fs.existsSync(p)) {
        return JSON.parse(fs.readFileSync(p, 'utf-8'));
      }
    } catch {}
    return null;
  }

  // --- 1. OBTENER ESTADO COMPLETO DE ACTUALIZACIÓN DEL CATÁLOGO ---
  async getCatalogStatus(checkRemote = true) {
    const localVersionObj = this.getLocalVersion();
    const localVersion = this.extractVersionString(localVersionObj);
    const localVersionReadable = this.formatReadableVersion(localVersionObj);

    let remoteVersionObj: any = null;
    let remoteVersion = localVersion;
    let remoteVersionReadable = localVersionReadable;
    let remoteCheckError: string | null = null;

    if (checkRemote) {
      try {
        remoteVersionObj = await this.fetchGzipJson(VERSION_URL);
        remoteVersion = this.extractVersionString(remoteVersionObj);
        remoteVersionReadable = this.formatReadableVersion(remoteVersionObj);
        this.lastCheckedAt = new Date().toISOString();
      } catch (err: any) {
        remoteCheckError = err.message;
      }
    }

    // Leer conteos del archivo catalogo.json local
    const localCat = this.getLocalCatalog();
    const fileMovies = Array.isArray(localCat?.peliculas) ? localCat.peliculas.length : 0;
    const fileSeries = Array.isArray(localCat?.series) ? localCat.series.length : 0;
    const fileDocs = Array.isArray(localCat?.documentales) ? localCat.documentales.length : 0;
    const fileShorts = Array.isArray(localCat?.cortometrajes) ? localCat.cortometrajes.length : 0;

    // Leer conteos en PostgreSQL
    const [dbByType, dbExclusive, dbSeasons, dbEpisodes, statusCfg] = await Promise.all([
      this.prisma.content.groupBy({
        by: ['type'],
        _count: { _all: true },
      }),
      this.prisma.content.count({ where: { isExclusive: true } }),
      this.prisma.season.count(),
      this.prisma.episode.count(),
      this.prisma.appConfig.findUnique({ where: { key: 'catalog_sync_status' } }),
    ]);

    const typeMap: Record<string, number> = {
      MOVIE: 0,
      SERIES: 0,
      DOCUMENTARY: 0,
      SHORT: 0,
    };
    dbByType.forEach((g) => {
      typeMap[g.type] = g._count._all;
    });
    const dbTotal = typeMap.MOVIE + typeMap.SERIES + typeMap.DOCUMENTARY + typeMap.SHORT;

    let savedStatus: any = null;
    if (statusCfg) {
      try {
        savedStatus = JSON.parse(statusCfg.value);
      } catch {}
    }

    const isVersionMatch = Boolean(localVersion && remoteVersion && localVersion === remoteVersion);
    const fileTotal = fileMovies + fileSeries + fileDocs + fileShorts;
    const isDbFullySynced = dbTotal >= fileTotal;

    return {
      isUpToDate: isVersionMatch,
      hasUpdateAvailable: Boolean(remoteVersion && localVersion && remoteVersion !== localVersion),
      isDbFullySynced,
      localVersion: localVersion || 'No registrada',
      localVersionReadable,
      remoteVersion: remoteVersion || 'No disponible',
      remoteVersionReadable,
      remoteCheckError,
      lastCheckedAt: this.lastCheckedAt || savedStatus?.lastCheckedAt || new Date().toISOString(),
      lastSyncedAt: savedStatus?.lastSyncedAt || null,
      lastNotifiedVersion: savedStatus?.lastNotifiedVersion || localVersion || null,
      autoCheckIntervalMinutes: 15,
      syncProgress: this.syncProgress,
      fileCounts: {
        movies: fileMovies,
        series: fileSeries,
        documentaries: fileDocs,
        shorts: fileShorts,
        total: fileTotal,
      },
      dbCounts: {
        movies: typeMap.MOVIE,
        series: typeMap.SERIES,
        documentaries: typeMap.DOCUMENTARY,
        shorts: typeMap.SHORT,
        exclusive: dbExclusive,
        total: dbTotal,
        seasons: dbSeasons,
        episodes: dbEpisodes,
      },
      lastSyncResult: savedStatus?.lastSyncResult || null,
      history: savedStatus?.history || [],
    };
  }

  // --- 2. VERIFICACIÓN AUTOMÁTICA / MANUAL DE NUEVA VERSIÓN (COMPATIBLE CON catalogService.js) ---
  async checkCatalogUpdatesAutomatic(forcePush = false) {
    try {
      const versionData = await this.fetchGzipJson(VERSION_URL);
      const serverVersionDate = this.extractVersionString(versionData);
      this.lastCheckedAt = new Date().toISOString();

      if (!serverVersionDate) {
        this.logger.warn('⚠️ [Servicio Catálogo] No se pudo extraer la versión del servidor remoto.');
        return { updated: false, message: 'No se pudo extraer la versión remota.' };
      }

      if (!forcePush && this.cachedVersionDate && this.cachedVersionDate === serverVersionDate) {
        return {
          updated: false,
          version: serverVersionDate,
          message: 'El catálogo ya se encuentra en la última versión.',
        };
      }

      const statusCfg = await this.prisma.appConfig.findUnique({
        where: { key: 'catalog_sync_status' },
      });
      let savedStatus: any = {};
      if (statusCfg) {
        try {
          savedStatus = JSON.parse(statusCfg.value);
        } catch {}
      }

      const lastNotifiedVersion = savedStatus.lastNotifiedVersion || null;
      const lastCatalogCounts = savedStatus.lastCatalogCounts || null;
      this.cachedVersionDate = serverVersionDate;

      if (!forcePush && lastNotifiedVersion === serverVersionDate) {
        return {
          updated: false,
          version: serverVersionDate,
          message: `El catálogo está al día (Versión: ${serverVersionDate}).`,
        };
      }

      this.logger.log(`✨ [Servicio Catálogo] ¡Procesando actualización del catálogo! (${serverVersionDate})`);

      // Descargar catálogo remoto y guardar en disco
      const newCatalog = await this.fetchGzipJson(CATALOG_URL);
      const dataDir = this.getDataDir();
      fs.writeFileSync(path.join(dataDir, 'catalogo.json'), JSON.stringify(newCatalog, null, 2), 'utf-8');
      fs.writeFileSync(path.join(dataDir, 'version.json'), JSON.stringify(versionData, null, 2), 'utf-8');

      // Recargar caché de Aura IA
      this.auraAiService.reloadCatalogCache();

      const newMovies = Array.isArray(newCatalog.peliculas) ? newCatalog.peliculas.length : 0;
      const newSeries = Array.isArray(newCatalog.series) ? newCatalog.series.length : 0;
      const newShorts = Array.isArray(newCatalog.cortometrajes) ? newCatalog.cortometrajes.length : 0;
      const newDocs = Array.isArray(newCatalog.documentales) ? newCatalog.documentales.length : 0;

      let moviesDiff = 0;
      let seriesDiff = 0;
      let shortsDiff = 0;
      let docsDiff = 0;

      if (lastCatalogCounts) {
        moviesDiff = Math.max(0, newMovies - (lastCatalogCounts.movies || 0));
        seriesDiff = Math.max(0, newSeries - (lastCatalogCounts.series || 0));
        shortsDiff = Math.max(0, newShorts - (lastCatalogCounts.shorts || 0));
        docsDiff = Math.max(0, newDocs - (lastCatalogCounts.documentaries || 0));
      }

      const totalNew = moviesDiff + seriesDiff + shortsDiff + docsDiff;

      // Sincronizar obras nuevas hacia PostgreSQL en segundo plano
      this.syncCatalogToPostgres(newCatalog).catch((e) =>
        this.logger.error(`Error sincronizando a PostgreSQL: ${e.message}`),
      );

      const historyEntry = {
        version: serverVersionDate,
        readableVersion: this.formatReadableVersion(versionData),
        timestamp: new Date().toISOString(),
        moviesDiff,
        seriesDiff,
        shortsDiff,
        docsDiff,
        totalNew,
        forcedPush: forcePush,
      };

      const updatedStatus = {
        lastNotifiedVersion: serverVersionDate,
        lastCheckedAt: new Date().toISOString(),
        lastSyncedAt: new Date().toISOString(),
        lastCatalogCounts: {
          movies: newMovies,
          series: newSeries,
          shorts: newShorts,
          documentaries: newDocs,
        },
        lastSyncResult: historyEntry,
        history: [historyEntry, ...(savedStatus.history || [])].slice(0, 15),
      };

      await this.prisma.appConfig.upsert({
        where: { key: 'catalog_sync_status' },
        update: { value: JSON.stringify(updatedStatus) },
        create: {
          key: 'catalog_sync_status',
          value: JSON.stringify(updatedStatus),
          description: 'Estado de sincronización y versiones del catálogo de Audiocinemateca',
        },
      });

      if (!forcePush && !lastNotifiedVersion) {
        this.logger.log(`ℹ️ [Servicio Catálogo] Versión inicial registrada (${serverVersionDate}). Push silenciado en primer inicio.`);
        return {
          updated: true,
          version: serverVersionDate,
          pushSent: false,
          diff: historyEntry,
          message: `Versión inicial ${serverVersionDate} registrada y sincronización con PostgreSQL iniciada.`,
        };
      }

      // Construir notificación Push para la comunidad
      let title = '¡Catálogo de Audiocinemateca actualizado!';
      let messageText =
        'El catálogo de Audiocinemateca ha sido actualizado con nuevos contenidos. ¡Entra a descubrirlos!';

      if (totalNew > 0) {
        title = '¡Nuevas aventuras te esperan!';
        const parts: string[] = [];
        if (moviesDiff > 0) parts.push(`${moviesDiff} película${moviesDiff > 1 ? 's' : ''}`);
        if (seriesDiff > 0) parts.push(`${seriesDiff} serie${seriesDiff > 1 ? 's' : ''}`);
        if (shortsDiff > 0) parts.push(`${shortsDiff} corto${shortsDiff > 1 ? 's' : ''}`);
        if (docsDiff > 0) parts.push(`${docsDiff} documental${docsDiff > 1 ? 'es' : ''}`);

        messageText = `Se han añadido ${totalNew} nuevos títulos al catálogo: ${parts.join(', ')}. ¡Entra ahora para descubrirlos!`;
      }

      const pushRes = await this.pushService.sendGlobalPush({
        title,
        body: messageText,
        destination: 'catalog',
      });

      return {
        updated: true,
        version: serverVersionDate,
        pushSent: pushRes.success,
        pushMessage: messageText,
        diff: historyEntry,
        message: `Catálogo actualizado a la versión ${serverVersionDate} y notificación Push enviada.`,
      };
    } catch (err: any) {
      this.logger.error(`❌ [Servicio Catálogo] Error verificando actualización: ${err.message}`);
      throw err;
    }
  }

  // --- 3. SINCRONIZACIÓN MASIVA ULTRARRÁPIDA A POSTGRESQL ---
  async triggerFullDatabaseSync(options: { downloadRemote?: boolean; notifyPush?: boolean } = {}) {
    if (this.syncProgress.isSyncing) {
      return {
        alreadyRunning: true,
        message: 'Ya hay una sincronización de base de datos en progreso.',
        syncProgress: this.syncProgress,
      };
    }

    let catalog = this.getLocalCatalog();
    let versionData = this.getLocalVersion();

    if (options.downloadRemote) {
      try {
        versionData = await this.fetchGzipJson(VERSION_URL);
        catalog = await this.fetchGzipJson(CATALOG_URL);
        const dataDir = this.getDataDir();
        fs.writeFileSync(path.join(dataDir, 'catalogo.json'), JSON.stringify(catalog, null, 2), 'utf-8');
        fs.writeFileSync(path.join(dataDir, 'version.json'), JSON.stringify(versionData, null, 2), 'utf-8');
        this.auraAiService.reloadCatalogCache();
      } catch (err: any) {
        this.logger.warn(`No se pudo descargar versión remota (${err.message}), usando catalogo.json local.`);
      }
    }

    if (!catalog) {
      throw new Error('No se encontró catalogo.json local ni remoto para sincronizar.');
    }

    // Ejecutar sincronización en segundo plano con reporte de progreso en tiempo real
    this.syncCatalogToPostgres(catalog, options.notifyPush).catch((err) => {
      this.logger.error(`Error en sincronización masiva: ${err.message}`);
    });

    return {
      started: true,
      message: 'Sincronización completa del catálogo hacia PostgreSQL iniciada.',
      syncProgress: this.syncProgress,
    };
  }

  private generateSlug(text: string): string {
    return (
      text
        .toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/(^-|-$)+/g, '')
        .substring(0, 80) || 'obra'
    );
  }

  private async syncCatalogToPostgres(catalog: any, notifyPush = false) {
    this.syncProgress = {
      isSyncing: true,
      stage: 'Analizando registros existentes en PostgreSQL...',
      processed: 0,
      total: 0,
      percent: 0,
      addedMovies: 0,
      addedSeries: 0,
      addedDocs: 0,
      addedShorts: 0,
      startedAt: new Date().toISOString(),
      completedAt: null,
      error: null,
    };

    try {
      const peliculas: any[] = catalog.peliculas || [];
      const series: any[] = catalog.series || [];
      const documentales: any[] = catalog.documentales || [];
      const cortometrajes: any[] = catalog.cortometrajes || [];

      const totalToProcess =
        peliculas.length + series.length + documentales.length + cortometrajes.length;
      this.syncProgress.total = totalToProcess;

      // Cargar IDs externos y slugs ya existentes en 1 sola consulta rápida
      const existingRecords = await this.prisma.content.findMany({
        select: { externalId: true, slug: true },
      });

      const existingExternalIds = new Set<string>();
      const existingSlugs = new Set<string>();
      for (const r of existingRecords) {
        if (r.externalId) existingExternalIds.add(r.externalId);
        if (r.slug) existingSlugs.add(r.slug);
      }

      const makeUniqueSlug = (title: string, prefix: string, rawId: string) => {
        const base = `${this.generateSlug(title)}-${prefix}-${rawId}`;
        if (!existingSlugs.has(base)) {
          existingSlugs.add(base);
          return base;
        }
        const fallback = `${base}-${Math.floor(1000 + Math.random() * 9000)}`;
        existingSlugs.add(fallback);
        return fallback;
      };

      // 1. Procesar Películas, Documentales y Cortos en lotes con createMany
      const unitCategories = [
        { list: peliculas, type: ContentType.MOVIE, prefix: 'movie', label: 'Películas' },
        { list: documentales, type: ContentType.DOCUMENTARY, prefix: 'doc', label: 'Documentales' },
        { list: cortometrajes, type: ContentType.SHORT, prefix: 'short', label: 'Cortometrajes' },
      ];

      for (const group of unitCategories) {
        this.syncProgress.stage = `Sincronizando ${group.label} (${group.list.length})...`;
        const toInsert: any[] = [];

        for (const item of group.list) {
          const rawId = String(item.id || '').trim();
          if (!rawId) {
            this.syncProgress.processed++;
            continue;
          }

          const prefixedExtId = `${group.prefix}_${rawId}`;
          // Soportar tanto el externalId sin prefijo de la siembra inicial como el prefijado
          if (existingExternalIds.has(prefixedExtId) || existingExternalIds.has(rawId)) {
            this.syncProgress.processed++;
            continue;
          }

          existingExternalIds.add(prefixedExtId);
          const title = (item.Titulo || item.titulo || 'Sin título').trim();
          const slug = makeUniqueSlug(title, group.prefix, rawId);
          const releaseYear = item.anio ? parseInt(String(item.anio), 10) : null;
          const durationMinutes = item.duracion ? parseInt(String(item.duracion), 10) : null;
          const enlaces = Array.isArray(item.enlaces) ? item.enlaces.filter(Boolean) : [];

          toInsert.push({
            externalId: prefixedExtId,
            title,
            slug,
            synopsis: item.sinopsis || 'Sinopsis no disponible.',
            type: group.type,
            releaseYear: releaseYear && !isNaN(releaseYear) ? releaseYear : null,
            duration: item.duracion ? `${item.duracion} min` : null,
            durationMinutes: durationMinutes && !isNaN(durationMinutes) ? durationMinutes : null,
            country: item.pais || null,
            director: item.director || null,
            screenplay: item.guion || null,
            music: item.musica || null,
            cinematography: item.fotografia || null,
            cast: item.reparto || null,
            production: item.productora || null,
            narrator: item.narracion || null,
            audioDescription: true,
            dubbing: item.idioma || 'Español',
            genreRaw: item.genero || null,
            filmaffinityUrl: item.filmaffinity || null,
            audioUrl: enlaces.length > 0 ? enlaces[0] : null,
            audioUrls: enlaces,
          });

          this.syncProgress.processed++;
        }

        // Insertar en lotes de 500
        const BATCH_SIZE = 500;
        for (let i = 0; i < toInsert.length; i += BATCH_SIZE) {
          const batch = toInsert.slice(i, i + BATCH_SIZE);
          const res = await this.prisma.content.createMany({
            data: batch,
            skipDuplicates: true,
          });
          if (group.type === ContentType.MOVIE) this.syncProgress.addedMovies += res.count;
          else if (group.type === ContentType.DOCUMENTARY) this.syncProgress.addedDocs += res.count;
          else if (group.type === ContentType.SHORT) this.syncProgress.addedShorts += res.count;

          this.syncProgress.percent = Math.round(
            (this.syncProgress.processed / Math.max(1, this.syncProgress.total)) * 100,
          );
        }
      }

      // 2. Procesar Series y sus Temporadas/Episodios
      this.syncProgress.stage = `Sincronizando Series (${series.length})...`;
      const seriesToCreate: any[] = [];
      const seriesRawMap = new Map<string, any>();

      for (const s of series) {
        const rawId = String(s.id || '').trim();
        if (!rawId) {
          this.syncProgress.processed++;
          continue;
        }

        const prefixedExtId = `series_${rawId}`;
        if (existingExternalIds.has(prefixedExtId) || existingExternalIds.has(rawId)) {
          this.syncProgress.processed++;
          continue;
        }

        existingExternalIds.add(prefixedExtId);
        const title = (s.titulo || s.Titulo || 'Serie sin título').trim();
        const slug = makeUniqueSlug(title, 'series', rawId);
        const releaseYear = s.anio ? parseInt(String(s.anio), 10) : null;

        seriesToCreate.push({
          externalId: prefixedExtId,
          title,
          slug,
          synopsis: s.sinopsis || 'Sinopsis no disponible.',
          type: ContentType.SERIES,
          releaseYear: releaseYear && !isNaN(releaseYear) ? releaseYear : null,
          country: s.pais || null,
          director: s.director || null,
          screenplay: s.guion || null,
          music: s.musica || null,
          cinematography: s.fotografia || null,
          cast: s.reparto || null,
          production: s.productora || null,
          narrator: s.narracion || null,
          audioDescription: true,
          dubbing: s.idioma || 'Español',
          genreRaw: s.genero || null,
          filmaffinityUrl: s.filmaffinity || null,
        });
        seriesRawMap.set(prefixedExtId, s);
        this.syncProgress.processed++;
      }

      if (seriesToCreate.length > 0) {
        const BATCH_SIZE = 300;
        for (let i = 0; i < seriesToCreate.length; i += BATCH_SIZE) {
          const batch = seriesToCreate.slice(i, i + BATCH_SIZE);
          const res = await this.prisma.content.createMany({
            data: batch,
            skipDuplicates: true,
          });
          this.syncProgress.addedSeries += res.count;
        }

        // Poblar temporadas y episodios de las series recién insertadas
        this.syncProgress.stage = 'Indexando temporadas y episodios de series...';
        const insertedSeries = await this.prisma.content.findMany({
          where: { externalId: { in: Array.from(seriesRawMap.keys()) } },
          select: { id: true, externalId: true },
        });

        for (const sRow of insertedSeries) {
          const rawSerie = sRow.externalId ? seriesRawMap.get(sRow.externalId) : null;
          if (!rawSerie?.capitulos || typeof rawSerie.capitulos !== 'object') continue;

          for (const [seasonKey, epList] of Object.entries(rawSerie.capitulos)) {
            if (!Array.isArray(epList) || epList.length === 0) continue;
            const seasonNum = parseInt(seasonKey.replace(/[^0-9]/g, ''), 10) || 1;

            try {
              const createdSeason = await this.prisma.season.upsert({
                where: {
                  contentId_seasonNumber: {
                    contentId: sRow.id,
                    seasonNumber: seasonNum,
                  },
                },
                update: {},
                create: {
                  contentId: sRow.id,
                  seasonNumber: seasonNum,
                  title: `Temporada ${seasonNum}`,
                },
              });

              const episodesData = epList.map((ep: any, idx: number) => ({
                seasonId: createdSeason.id,
                episodeNumber: parseInt(String(ep.capitulo), 10) || idx + 1,
                title: ep.titulo || `Episodio ${idx + 1}`,
                audioUrl: ep.enlace || '',
              }));

              await this.prisma.episode.createMany({
                data: episodesData,
                skipDuplicates: true,
              });
            } catch {}
          }
        }
      }

      this.syncProgress.percent = 100;
      this.syncProgress.stage = 'Sincronización completada con éxito';
      this.syncProgress.completedAt = new Date().toISOString();
      this.syncProgress.isSyncing = false;

      const totalAdded =
        this.syncProgress.addedMovies +
        this.syncProgress.addedSeries +
        this.syncProgress.addedDocs +
        this.syncProgress.addedShorts;

      this.logger.log(
        `✅ [Servicio Catálogo] Sincronización PostgreSQL finalizada: +${totalAdded} nuevas obras importadas.`,
      );

      if (notifyPush) {
        const msg =
          totalAdded > 0
            ? `Se han sincronizado ${totalAdded} nuevos títulos en Audiocinemateca (${this.syncProgress.addedMovies} películas, ${this.syncProgress.addedSeries} series, ${this.syncProgress.addedDocs} documentales, ${this.syncProgress.addedShorts} cortos).`
            : 'El catálogo de Audiocinemateca ha sido verificado y actualizado con las últimas novedades.';
        await this.pushService.sendGlobalPush({
          title: '¡Catálogo de Audiocinemateca actualizado!',
          body: msg,
          destination: 'catalog',
        });
      }
    } catch (err: any) {
      this.syncProgress.error = err.message;
      this.syncProgress.stage = `Error: ${err.message}`;
      this.syncProgress.isSyncing = false;
      throw err;
    }
  }

  // --- 4. BUSCADOR EXACTO POR URL O ID (USADO POR BANNER Y RANKINGS) ---
  public parseUrlIdAndHint(linkUrl: string): { itemId: string; typeHint: string | null } {
    let itemId = '';
    let typeHint: string | null = null;

    try {
      const cleanUrl = linkUrl.trim().replace(/\/$/, '');
      if (cleanUrl.includes('?')) {
        const urlObj = new URL(cleanUrl.startsWith('http') ? cleanUrl : `https://audiocinemateca.com/${cleanUrl}`);
        itemId = urlObj.searchParams.get('id') || urlObj.searchParams.get('itemId') || '';
      }

      if (!itemId) {
        const parts = cleanUrl.split('/');
        const lastSeg = parts[parts.length - 1];
        if (
          lastSeg &&
          !['serie', 'series', 'pelicula', 'peliculas', 'documental', 'documentales', 'cortometraje', 'cortometrajes'].includes(
            lastSeg.toLowerCase(),
          )
        ) {
          itemId = lastSeg;
        }
      }

      const lower = cleanUrl.toLowerCase();
      if (lower.includes('/serie')) typeHint = 'series';
      else if (lower.includes('/pelicula') || lower.includes('/movie')) typeHint = 'peliculas';
      else if (lower.includes('/documental')) typeHint = 'documentales';
      else if (lower.includes('/cortometraje')) typeHint = 'cortometrajes';
    } catch {}

    return { itemId, typeHint };
  }

  public findItemInCatalog(linkOrId: string) {
    const { itemId, typeHint } = this.parseUrlIdAndHint(linkOrId);
    const catalog = this.getLocalCatalog();
    if (!catalog || !itemId) return null;

    const groups = [
      { type: 'peliculas', itemType: 'pelicula', items: catalog.peliculas || [] },
      { type: 'series', itemType: 'serie', items: catalog.series || [] },
      { type: 'documentales', itemType: 'documental', items: catalog.documentales || [] },
      { type: 'cortometrajes', itemType: 'cortometraje', items: catalog.cortometrajes || [] },
    ];

    if (typeHint) {
      groups.sort((a, b) => (a.type === typeHint ? -1 : b.type === typeHint ? 1 : 0));
    }

    for (const g of groups) {
      const found = g.items.find(
        (it: any) => String(it.id || '').trim().toLowerCase() === itemId.toLowerCase(),
      );
      if (found) {
        const rawGenres = found.genero || found.Genero || '';
        const genres = rawGenres
          .split(/[.|,]/)
          .map((x: string) => x.trim())
          .filter(Boolean);

        return {
          item: found,
          itemId: String(found.id),
          itemType: g.itemType,
          categorySlug: g.type,
          title: found.Titulo || found.titulo || found.title || '',
          synopsis: found.sinopsis || '',
          year: found.anio || found.año || '',
          director: found.director || '',
          narrator: found.narracion || '',
          genres: genres.length > 0 ? genres : ['Audio'],
          filmaffinity: found.filmaffinity || found.Filmaffinity || null,
          url: `https://audiocinemateca.com/${g.type}?id=${found.id}`,
        };
      }
    }

    return null;
  }

  // --- 5. AUDITOR DE ENLACES DE AUDIO DEL CATÁLOGO ---
  async auditSampleAudioLinks(sampleSize = 15) {
    const contents = await this.prisma.content.findMany({
      where: { audioUrl: { not: null } },
      take: sampleSize,
      orderBy: { updatedAt: 'desc' },
      select: { id: true, title: true, type: true, audioUrl: true },
    });

    const authHeader = this.getAuthHeader();
    const results: any[] = [];

    for (const item of contents) {
      if (!item.audioUrl) continue;
      let targetUrl = item.audioUrl.trim();
      if (!targetUrl.startsWith('http://') && !targetUrl.startsWith('https://')) {
        targetUrl = `https://audiocinemateca.com/${targetUrl.replace(/^\/+/, '')}`;
      }

      const status = await new Promise<{ ok: boolean; statusCode: number }>((resolve) => {
        try {
          const parsed = new URL(targetUrl);
          const mod = parsed.protocol === 'https:' ? https : http;
          const req = mod.request(
            parsed,
            {
              method: 'HEAD',
              headers: { Authorization: authHeader, 'User-Agent': 'Audiocinemateca-Auditor/3.1' },
              timeout: 6000,
            },
            (res) => {
              const code = res.statusCode || 0;
              resolve({ ok: code >= 200 && code < 400, statusCode: code });
            },
          );
          req.on('timeout', () => {
            req.destroy();
            resolve({ ok: false, statusCode: 408 });
          });
          req.on('error', () => resolve({ ok: false, statusCode: 0 }));
          req.end();
        } catch {
          resolve({ ok: false, statusCode: 0 });
        }
      });

      results.push({
        id: item.id,
        title: item.title,
        type: item.type,
        audioUrl: targetUrl,
        ok: status.ok,
        statusCode: status.statusCode,
      });
    }

    const healthyCount = results.filter((r) => r.ok).length;
    return {
      auditedCount: results.length,
      healthyCount,
      brokenCount: results.length - healthyCount,
      checkedAt: new Date().toISOString(),
      results,
    };
  }
}
