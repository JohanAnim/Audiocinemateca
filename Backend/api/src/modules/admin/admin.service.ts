import { Injectable, Logger, OnModuleInit, BadRequestException } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service.js';
import { AuraAiService } from '../ai/aura-ai.service.js';
import { PushService, GlobalPushPayload } from '../notifications/push.service.js';
import { AppConfigService } from '../app-config/app-config.service.js';
import { CatalogSyncService } from '../catalog-sync/catalog-sync.service.js';

export interface AdminAgentToolProposal {
  tool:
    | 'send_push_notification'
    | 'publish_banner'
    | 'create_exclusive_content'
    | 'publish_announcement'
    | 'send_global_chat_message'
    | 'regenerate_collections'
    | 'recalculate_weekly_ranking'
    | 'sync_catalog'
    | 'toggle_maintenance';
  mode: 'propose' | 'execute';
  summary: string;
  params: Record<string, any>;
}

@Injectable()
export class AdminService implements OnModuleInit {
  private readonly logger = new Logger(AdminService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly auraAiService: AuraAiService,
    private readonly pushService: PushService,
    private readonly appConfigService: AppConfigService,
    private readonly catalogSyncService: CatalogSyncService,
  ) {}

  async onModuleInit() {
    setTimeout(() => {
      this.ensureWeeklyRankingExists().catch((e) =>
        this.logger.warn(`No se pudo inicializar ranking semanal: ${e.message}`),
      );
    }, 8000);
  }

  private async ensureWeeklyRankingExists() {
    const existing = await this.prisma.appConfig.findUnique({
      where: { key: 'weekly_top_10_ranking' },
    });
    if (!existing) {
      await this.recalculateWeeklyRanking();
    }
  }

  async getExecutiveStats() {
    const [
      totalUsers,
      onlineUsers,
      totalContents,
      contentsByType,
      exclusiveCount,
      totalSeasons,
      totalEpisodes,
      totalComments,
      totalRatings,
      viewsAggregate,
      ratingsAggregate,
      devices,
      totalMessages,
      activeBans,
    ] = await Promise.all([
      this.prisma.user.count(),
      this.prisma.user.count({ where: { isOnline: true } }),
      this.prisma.content.count(),
      this.prisma.content.groupBy({
        by: ['type'],
        _count: { _all: true },
      }),
      this.prisma.content.count({ where: { isExclusive: true } }),
      this.prisma.season.count(),
      this.prisma.episode.count(),
      this.prisma.comment.count(),
      this.prisma.rating.count(),
      this.prisma.content.aggregate({
        _sum: { viewsCount: true },
      }),
      this.prisma.content.aggregate({
        _avg: { averageRating: true },
      }),
      this.prisma.userDevice.findMany({
        select: { deviceInfo: true, osVersion: true },
      }),
      this.prisma.chatMessage.count(),
      this.prisma.bannedUser.count(),
    ]);

    const platformCounts: Record<string, number> = {
      Android: 0,
      Windows: 0,
      macOS: 0,
      Linux: 0,
      Web: 0,
      Otros: 0,
    };

    devices.forEach((d) => {
      const info = (d.deviceInfo || '').toLowerCase();
      const os = (d.osVersion || '').toLowerCase();
      if (info.includes('android') || os.includes('android')) {
        platformCounts.Android++;
      } else if (info.includes('windows') || os.includes('windows')) {
        platformCounts.Windows++;
      } else if (info.includes('mac') || info.includes('darwin')) {
        platformCounts.macOS++;
      } else if (info.includes('linux')) {
        platformCounts.Linux++;
      } else if (info.includes('web') || info.includes('browser')) {
        platformCounts.Web++;
      } else {
        platformCounts.Otros++;
      }
    });

    const memoryUsage = process.memoryUsage();

    return {
      users: {
        total: totalUsers,
        onlineNow: onlineUsers,
        activeBans,
        registeredDevices: devices.length,
      },
      platforms: platformCounts,
      catalog: {
        total: totalContents,
        exclusive: exclusiveCount,
        byType: contentsByType.reduce((acc, curr) => {
          acc[curr.type] = curr._count._all;
          return acc;
        }, {} as Record<string, number>),
        seasons: totalSeasons,
        episodes: totalEpisodes,
      },
      engagement: {
        totalViews: viewsAggregate._sum.viewsCount || 0,
        totalRatings,
        averageRating: Number((ratingsAggregate._avg.averageRating || 0).toFixed(2)),
        totalComments,
        totalChatMessages: totalMessages,
      },
      system: {
        uptimeSeconds: Math.floor(process.uptime()),
        memoryRssMb: Math.round(memoryUsage.rss / 1024 / 1024),
        memoryHeapUsedMb: Math.round(memoryUsage.heapUsed / 1024 / 1024),
        nodeVersion: process.version,
        serverTime: new Date().toISOString(),
      },
    };
  }

  // --- RANKING SEMANAL TOP 10 (ALGORITMO PONDERADO + MODO MANUAL) ---
  async getWeeklyRanking() {
    const record = await this.prisma.appConfig.findUnique({
      where: { key: 'weekly_top_10_ranking' },
    });
    if (record) {
      try {
        return JSON.parse(record.value);
      } catch {
        // continuar
      }
    }
    return this.recalculateWeeklyRanking();
  }

  async recalculateWeeklyRanking() {
    this.logger.log('📊 Calculando Ranking Top 10 Semanal ponderado en PostgreSQL...');

    const contents = await this.prisma.content.findMany({
      where: { status: 'PUBLISHED' },
      take: 150,
      orderBy: [{ viewsCount: 'desc' }, { averageRating: 'desc' }],
      include: {
        _count: {
          select: {
            favorites: true,
            comments: true,
            ratings: true,
            playbackProgress: true,
          },
        },
      },
    });

    const scored = contents.map((c) => {
      const ratingPoints = (c.averageRating || 0) * (c._count.ratings || 1) * 2.5;
      const favoritePoints = (c._count.favorites || 0) * 4;
      const commentPoints = (c._count.comments || 0) * 3;
      const progressPoints = (c._count.playbackProgress || 0) * 5;
      const viewPoints = (c.viewsCount || 0) * 1.2;

      const totalScore = Math.round(
        ratingPoints + favoritePoints + commentPoints + progressPoints + viewPoints,
      );

      return {
        contentId: c.externalId || c.id,
        uuid: c.id,
        title: c.title,
        type: c.type,
        year: c.releaseYear,
        genre: c.genreRaw,
        averageRating: Number((c.averageRating || 0).toFixed(1)),
        viewsCount: c.viewsCount,
        score: totalScore > 0 ? totalScore : 10,
      };
    });

    scored.sort((a, b) => b.score - a.score);

    const top10 = scored.slice(0, 10).map((item, idx) => ({
      rank: idx + 1,
      ...item,
    }));

    const payload = {
      items: top10,
      updatedAt: Date.now(),
      updatedBy: 'auto_algorithm',
    };

    await this.appConfigService.upsertConfig(
      'weekly_top_10_ranking',
      payload,
      'Ranking Top 10 Semanal de la Comunidad',
    );

    return payload;
  }

  async saveManualWeeklyRanking(items: any[]) {
    const normalized = items.slice(0, 10).map((item, idx) => ({
      rank: idx + 1,
      contentId: String(item.contentId || item.id || ''),
      title: String(item.title || '').trim(),
      type: item.type || 'MOVIE',
      score: Number(item.score) || 100 - idx * 5,
    }));

    const payload = {
      items: normalized,
      updatedAt: Date.now(),
      updatedBy: 'admin_manual',
    };

    await this.appConfigService.upsertConfig(
      'weekly_top_10_ranking',
      payload,
      'Ranking Top 10 Semanal (Editado manualmente por Admin)',
    );

    return payload;
  }

  // --- COLECCIONES CURADAS POR IA ---
  async getCuratedCollections() {
    return this.auraAiService.getCuratedCollections();
  }

  async regenerateCuratedCollections() {
    return this.auraAiService.generateCuratedCollectionsIfNeeded(true);
  }

  // --- NOTIFICACIONES PUSH Y REDACCIÓN CON IA ---
  async sendPushNotification(payload: GlobalPushPayload, sentBy: string) {
    return this.pushService.sendGlobalPush({
      ...payload,
      sentBy,
    });
  }

  async getPushHistory() {
    return this.pushService.getPushHistory();
  }

  async generatePushCopyWithAi(payload: {
    draftTitle?: string;
    draftMessage?: string;
    contentTitle?: string;
    contentType?: string;
    contextType?: 'banner' | 'push';
  }) {
    const title = payload.draftTitle || payload.contentTitle || 'Nuevo estreno en Audiocinemateca';
    const desc = payload.draftMessage || `Descubre esta obra (${payload.contentType || 'cine'}) con audiodescripción.`;
    return this.auraAiService.generatePushCopyWithAi(title, desc);
  }

  // --- CONFIGURACIÓN DEL CEREBRO MULTI-PROVEEDOR DE AURA 2.0 ---
  async getAuraBrainConfig() {
    return this.auraAiService.getBrainConfigForAdmin();
  }

  async saveAuraBrainConfig(body: any) {
    return this.auraAiService.saveBrainConfigFromAdmin(body);
  }

  async fetchProviderModelsLive(provider: 'groq' | 'gemini' | 'custom', customBaseUrl?: string) {
    return this.auraAiService.fetchProviderModelsLive(provider, customBaseUrl);
  }

  async testAuraBrainLive(body: {
    prompt?: string;
    overrideConfig?: any;
    tempApiKey?: string;
  }) {
    return this.auraAiService.testBrainInLive(body || {});
  }

  // --- MODERACIÓN Y PURGA DE CHAT ---
  async purgeChatMessages(options: { count?: number; olderThanDays?: number }) {
    if (options.olderThanDays && options.olderThanDays > 0) {
      const cutoff = new Date(Date.now() - options.olderThanDays * 24 * 3600 * 1000);
      const deleted = await this.prisma.chatMessage.deleteMany({
        where: { createdAt: { lt: cutoff } },
      });
      return {
        success: true,
        deletedCount: deleted.count,
        message: `Se purgaron ${deleted.count} mensajes con más de ${options.olderThanDays} días de antigüedad.`,
      };
    }

    const countToDelete = Math.min(Math.max(Number(options.count) || 50, 1), 1000);
    const oldest = await this.prisma.chatMessage.findMany({
      orderBy: { createdAt: 'asc' },
      take: countToDelete,
      select: { id: true },
    });

    if (oldest.length === 0) {
      return {
        success: true,
        deletedCount: 0,
        message: 'No hay mensajes en el chat para purgar.',
      };
    }

    const deleted = await this.prisma.chatMessage.deleteMany({
      where: { id: { in: oldest.map((m) => m.id) } },
    });

    return {
      success: true,
      deletedCount: deleted.count,
      message: `Se eliminaron los ${deleted.count} mensajes más antiguos del chat global.`,
    };
  }

  // ===========================================================================
  // AGENTE CENTRAL DEL PANEL DE CONTROL: CHAT ADMINISTRATIVO CON HERRAMIENTAS
  // ===========================================================================

  async chatWithAuraAdmin(
    messages: Array<{
      role: 'user' | 'assistant';
      content: string;
      proposal?: AdminAgentToolProposal | null;
      executed?: boolean;
    }>,
    adminUser: { id: string; username: string; displayName?: string | null },
  ) {
    const cleanMessages = Array.isArray(messages) ? messages.slice(-14) : [];
    const lastUserMsg =
      [...cleanMessages].reverse().find((m) => m.role === 'user')?.content || '';

    if (!lastUserMsg.trim()) {
      throw new BadRequestException('El mensaje no puede estar vacío.');
    }

    // Buscar si hay alguna propuesta previa pendiente de confirmación
    const pendingProposalMsg = [...cleanMessages]
      .reverse()
      .find((m) => m.role === 'assistant' && m.proposal && !m.executed);
    const pendingProposal = pendingProposalMsg?.proposal || null;

    // 1. Obtener contexto en tiempo real de la plataforma
    const [
      totalUsers,
      onlineUsers,
      totalContents,
      exclusiveContents,
      activeBanner,
      maintenanceCfg,
    ] = await Promise.all([
      this.prisma.user.count(),
      this.prisma.user.count({ where: { isOnline: true } }),
      this.prisma.content.count(),
      this.prisma.content.count({ where: { isExclusive: true } }),
      this.appConfigService.getActiveBanner(),
      this.appConfigService.getParsedConfig<any>('maintenance_mode'),
    ]);

    // 2. Búsqueda automática en el catálogo maestro (11,432 obras) y PostgreSQL
    // usando los últimos mensajes del usuario para encontrar películas/series citadas
    const recentUserTexts = cleanMessages
      .filter((m) => m.role === 'user')
      .slice(-2)
      .map((m) => m.content)
      .join(' ');

    const catalogMatches = this.auraAiService.searchRelevantCatalogItems(
      recentUserTexts,
      14,
      true,
    );

    const rawCatalog = this.auraAiService.getCatalog();
    const pelisList: any[] = rawCatalog?.peliculas || [];
    const seriesList: any[] = rawCatalog?.series || [];

    const latestAddedPelis = pelisList.slice(-4).reverse();
    const latestAddedSeries = seriesList.slice(-4).reverse();
    const oldestPelis = pelisList.slice(0, 3);
    const oldestSeries = seriesList.slice(0, 3);

    const dbMatches = await this.prisma.content.findMany({
      where: {
        OR: [
          { title: { contains: lastUserMsg.trim().slice(0, 40), mode: 'insensitive' } },
          { genreRaw: { contains: lastUserMsg.trim().slice(0, 40), mode: 'insensitive' } },
          { narrator: { contains: lastUserMsg.trim().slice(0, 40), mode: 'insensitive' } },
          { isExclusive: true },
        ],
      },
      take: 6,
      orderBy: { createdAt: 'desc' },
      select: {
        id: true,
        externalId: true,
        title: true,
        type: true,
        releaseYear: true,
        genreRaw: true,
        director: true,
        narrator: true,
        isExclusive: true,
        status: true,
        synopsis: true,
      },
    });

    const catalogContextLines: string[] = [];
    if (catalogMatches.items && catalogMatches.items.length > 0) {
      catalogContextLines.push(
        '[RESULTADOS ENCONTRADOS EN EL CATÁLOGO MAESTRO DE AUDIOCINEMATECA (11,432 OBRAS)]:',
      );
      for (const item of catalogMatches.items) {
        catalogContextLines.push(
          `- ID: "${item.id}" | Categoría: "${item.categoryKey}" | Título: "${item.title}" (${item.year}) | Género: ${item.genre} | Director: ${item.director} | Reparto: ${item.reparto} | Narración: ${item.narracion} | Enlace directo: ${item.url} | Sinopsis: ${item.sinopsis}`,
        );
      }
    }

    if (latestAddedPelis.length > 0 || latestAddedSeries.length > 0) {
      catalogContextLines.push(
        '\n[ÚLTIMAS NOVEDADES INCORPORADAS AL CATÁLOGO (AÑOS 2025-2026 - IDs MÁS ALTOS)]:',
      );
      for (const p of latestAddedPelis) {
        catalogContextLines.push(
          `- Película ID: "${p.id}" | "${p.titulo || p.Titulo}" (${p.anio || p.año}) | Género: ${p.genero} | Narración: ${p.narracion || 'N/A'} | Enlace: https://audiocinemateca.com/peliculas?id=${p.id}`,
        );
      }
      for (const s of latestAddedSeries) {
        catalogContextLines.push(
          `- Serie ID: "${s.id}" | "${s.titulo || s.Titulo}" (${s.anio || s.año}) | Género: ${s.genero} | Narración: ${s.narracion || 'N/A'} | Enlace: https://audiocinemateca.com/series?id=${s.id}`,
        );
      }
    }

    if (oldestPelis.length > 0 || oldestSeries.length > 0) {
      catalogContextLines.push(
        '\n[PRIMERAS OBRAS HISTÓRICAS INCORPORADAS AL CATÁLOGO (CLÁSICOS DE LOS AÑOS 70/80 - IDs MÁS BAJOS)]:',
      );
      for (const p of oldestPelis) {
        catalogContextLines.push(
          `- Película ID: "${p.id}" | "${p.titulo || p.Titulo}" (${p.anio || p.año}) | Género: ${p.genero} | Narración: ${p.narracion || 'N/A'} | Enlace: https://audiocinemateca.com/peliculas?id=${p.id}`,
        );
      }
      for (const s of oldestSeries) {
        catalogContextLines.push(
          `- Serie ID: "${s.id}" | "${s.titulo || s.Titulo}" (${s.anio || s.año}) | Género: ${s.genero} | Narración: ${s.narracion || 'N/A'} | Enlace: https://audiocinemateca.com/series?id=${s.id}`,
        );
      }
    }

    if (dbMatches.length > 0) {
      catalogContextLines.push(
        '\n[OBRAS RECIENTES / EXCLUSIVAS EN POSTGRESQL]:',
      );
      for (const d of dbMatches) {
        catalogContextLines.push(
          `- UUID: "${d.id}" | ExternalID: "${d.externalId || 'N/A'}" | Título: "${d.title}" (${d.releaseYear || 'N/A'}) | Tipo: ${d.type} | Exclusiva: ${d.isExclusive ? 'SÍ' : 'NO'} | Estado: ${d.status}`,
        );
      }
    }

    const auraCfg = await this.auraAiService.getAuraConfig();
    const adminName = adminUser.displayName || adminUser.username || 'Administrador';

    const systemPrompt = `${auraCfg.systemPrompt}

===================================================================
MODO ACTUAL: AGENTE CENTRAL DEL PANEL DE CONTROL (ADMINISTRACIÓN)
===================================================================
Estás conversando con el administrador "${adminName}" dentro del Panel de Control de Audiocinemateca.
Tienes acceso en tiempo real a las métricas y herramientas administrativas de la plataforma:

ESTADO ACTUAL DE LA PLATAFORMA:
- Usuarios registrados: ${totalUsers} (${onlineUsers} conectados ahora mismo)
- Obras en PostgreSQL: ${totalContents} (${exclusiveContents} exclusivas)
- Banner activo actual: ${activeBanner?.enabled ? `"${activeBanner.title}" -> ${activeBanner.actionUrl || 'Sin enlace'}` : 'Desactivado'}
- Modo Mantenimiento: ${maintenanceCfg?.enabled ? 'ACTIVO' : 'Inactivo'}

${catalogContextLines.join('\n')}

${
  pendingProposal
    ? `\n[PROPUESTA PENDIENTE DE CONFIRMACIÓN ANTERIOR]:\nHerramienta: ${pendingProposal.tool}\nResumen: ${pendingProposal.summary}\nParámetros preparados: ${JSON.stringify(pendingProposal.params)}\nIMPORTANTE: Si el administrador en su último mensaje está confirmando o aprobando esta propuesta (ej: "sí", "publícala", "hazlo", "adelante", "confirmo", "envíala", "créala"), debes devolver la misma herramienta con "mode": "execute" (aplicando cualquier ajuste que haya pedido).`
    : ''
}

HERRAMIENTAS ADMINISTRATIVAS DISPONIBLES (puedes proponerlas o ejecutarlas):
1. "send_push_notification": Enviar una notificación Push FCM a todos los usuarios.
   - Si es una recomendación de una película, serie, documental o corto del catálogo, usa:
     "destination": "content_detail",
     "contentId": "<ID numérico exacto del catálogo, ej. 150>",
     "contentType": "peliculas" | "series" | "documentales" | "cortometrajes",
     "contentTitle": "<Título de la obra>",
     "url": "<Enlace directo https://audiocinemateca.com/peliculas?id=...>",
     "title": "<Título atractivo máx 45 caracteres>",
     "body": "<Descripción atractiva máx 140 caracteres>",
     "target": "all",
     "delayMinutes": 0
   - Si es otro tipo de notificación, elige "destination" entre: "app" (inicio), "announcements" (comunicados), "global_chat" (chat global), "external_url" (con "url"), o "donation" (donaciones).

2. "publish_banner": Publicar o actualizar el Banner Destacado en la pantalla de inicio.
   - Parámetros:
     "title": "<Título del banner>",
     "message": "<Descripción atractiva para el banner>",
     "actionUrl": "<Enlace directo del catálogo https://audiocinemateca.com/... o URL externa>",
     "actionText": "Escuchar ahora",
     "type": "info" | "update" | "warning" | "alert",
     "sendPush": false (o true si el administrador pide enviar también notificación push)

3. "create_exclusive_content": Crear una nueva ficha en el Catálogo Exclusivo de Audiocinemateca (queda lista en borrador para editar y subir los archivos de audio y portada).
   - Parámetros:
     "title": "<Título de la obra>",
     "type": "MOVIE" | "SERIES" | "DOCUMENTARY" | "SHORT",
     "synopsis": "<Sinopsis redactada profesionalmente>",
     "releaseYear": 2026,
     "country": "<País>",
     "director": "<Director>",
     "cast": "<Reparto>",
     "narrator": "<Narrador de audiodescripción>",
     "dubbing": "Español Latino",
     "genreRaw": "<Géneros separados por coma>",
     "status": "DRAFT",
     "isExclusive": true

4. "publish_announcement": Publicar un comunicado oficial en la sección de Anuncios de la comunidad.
   - Parámetros:
     "title": "<Título del comunicado>",
     "content": "<Contenido completo del comunicado>",
     "sendPush": true

5. "send_global_chat_message": Publicar un mensaje en vivo como @Aura en el Chat Global de usuarios.
   - Parámetros:
     "message": "<Mensaje para la comunidad en el chat global>"

6. "regenerate_collections": Regenerar ahora mismo las 3 Colecciones Temáticas Curadas por IA.
   - Parámetros: {}

7. "recalculate_weekly_ranking": Recalcular el Ranking Top 10 Semanal.
   - Parámetros: {}

8. "sync_catalog": Sincronizar el catálogo maestro con audiocinemateca.com.
   - Parámetros: { "notifyUsers": false }

9. "toggle_maintenance": Activar o desactivar el Modo Mantenimiento.
   - Parámetros: { "enabled": true | false, "message": "<Mensaje de mantenimiento>" }

REGLA DE ORO DE INTERACCIÓN Y CONFIRMACIÓN:
- Cuando el administrador te pida enviar una notificación, publicar un banner, crear una nueva ficha exclusiva, publicar un comunicado o mandar un mensaje al chat global por primera vez:
  Usa SIEMPRE "mode": "propose". Redacta todos los campos en "params", explícale en "reply" lo que has preparado (incluyendo el título y enlace si es del catálogo) y pregúntale claramente si desea publicarlo/crearlo ahora mismo.
- Cuando el administrador confirme (ej. "sí, publícala", "hazlo ahora", "adelante", "confirmo") o te pida explícitamente ejecutar una tarea de mantenimiento/colecciones/ranking de inmediato:
  Usa "mode": "execute".

PAUTAS DE COMUNICACIÓN Y ELOCUENCIA CON EL ADMINISTRADOR:
- EXTENSIÓN Y PROFUNDIDAD ADAPTATIVA:
  * NUNCA seas telegráfica, seca ni excesivamente resumida. Habla como una verdadera copiloto ejecutiva y cinéfila apasionada.
  * Si el administrador te hace preguntas abiertas, te pide análisis, opiniones de cine, recomendaciones temáticas, ideas o consulta las estadísticas de la plataforma:
    Ofrece una respuesta completa, rica en vocabulario, bien argumentada y con buen cuerpo de texto en "reply" (varios párrafos desarrollados si el tema lo merece).
  * Cuando propongas una acción (Push, Banner, Contenido Exclusivo, Comunicado):
    En "reply", explica con entusiasmo por qué seleccionaste esa obra o enfoque, qué ganchos tiene para la comunidad de personas con discapacidad visual y amantes del cine, y presenta la propuesta invitando cordialmente a confirmar la publicación.
  * Si te saluda o es un intercambio rápido, sé cordial, chispeante y cercana sin sonar robótica ni apresurada.

FORMATO DE RESPUESTA OBLIGATORIO:
Responde ÚNICAMENTE con un objeto JSON válido con esta estructura exacta (sin bloques markdown adicionales si es posible):
{
  "reply": "Tu respuesta conversacional rica, detallada y expresiva para el administrador...",
  "action": null
}
O cuando prepares/ejecutes una herramienta:
{
  "reply": "Tu explicación apasionada y detallada de lo que preparaste y por qué es una excelente recomendación, preguntando si la confirmas...",
  "action": {
    "tool": "send_push_notification",
    "mode": "propose",
    "summary": "Notificación Push: Recomendación de ...",
    "params": { ... }
  }
}`;

    const conversationTranscript = cleanMessages
      .map((m) => {
        const prefix = m.role === 'user' ? `Administrador (${adminName})` : 'Aura';
        const propInfo = m.proposal
          ? `\n[Propuesta adjunta: ${m.proposal.tool} -> ${JSON.stringify(m.proposal.params)}]`
          : '';
        return `${prefix}: ${m.content}${propInfo}`;
      })
      .join('\n\n');

    const userPrompt = `Historial de la conversación administrativa:\n${conversationTranscript}\n\nResponde al último mensaje del administrador en el formato JSON indicado.`;

    const startTime = Date.now();
    const rawResponse = await this.auraAiService.generateWithConfiguredBrain(
      systemPrompt,
      userPrompt,
      2400,
    );
    const latencyMs = Date.now() - startTime;

    if (!rawResponse) {
      throw new BadRequestException(
        'Aura no pudo responder en este momento. Verifica que el proveedor de IA seleccionado tenga una Clave API válida en la pestaña Aura IA.',
      );
    }

    let replyText = rawResponse.trim();
    let parsedAction: AdminAgentToolProposal | null = null;

    try {
      let cleanJson = replyText;
      if (cleanJson.startsWith('```')) {
        cleanJson = cleanJson.replace(/^```(?:json)?\n?/, '').replace(/\n?```$/, '').trim();
      }
      const jsonMatch = cleanJson.match(/\{[\s\S]*\}/);
      if (jsonMatch) {
        const parsed = JSON.parse(jsonMatch[0]);
        if (typeof parsed.reply === 'string' && parsed.reply.trim()) {
          replyText = parsed.reply.trim();
        }
        if (parsed.action && parsed.action.tool) {
          parsedAction = {
            tool: parsed.action.tool,
            mode: parsed.action.mode === 'execute' ? 'execute' : 'propose',
            summary: parsed.action.summary || this.getDefaultToolSummary(parsed.action.tool),
            params: parsed.action.params || {},
          };
        }
      }
    } catch {
      // Si el modelo respondió en texto plano sin JSON válido, conservamos el texto limpio
      replyText = rawResponse.replace(/```json[\s\S]*?```/g, '').trim() || rawResponse;
    }

    // Si Aura determinó que el administrador confirmó la ejecución ("mode": "execute"),
    // ejecutamos la herramienta inmediatamente en el servidor
    let executedResult: any = null;
    if (parsedAction && parsedAction.mode === 'execute') {
      executedResult = await this.executeAuraAdminTool(
        parsedAction.tool,
        parsedAction.params,
        adminUser,
      );
    }

    return {
      reply: replyText,
      provider: auraCfg.provider,
      model: auraCfg.model,
      latencyMs,
      proposal: parsedAction && parsedAction.mode === 'propose' ? parsedAction : null,
      executedResult,
    };
  }

  private getDefaultToolSummary(tool: string): string {
    const map: Record<string, string> = {
      send_push_notification: 'Enviar Notificación Push',
      publish_banner: 'Publicar Banner Destacado',
      create_exclusive_content: 'Crear Nueva Ficha Exclusiva',
      publish_announcement: 'Publicar Comunicado Oficial',
      send_global_chat_message: 'Enviar Mensaje al Chat Global',
      regenerate_collections: 'Regenerar Colecciones Temáticas IA',
      recalculate_weekly_ranking: 'Recalcular Ranking Top 10 Semanal',
      sync_catalog: 'Sincronizar Catálogo Maestro',
      toggle_maintenance: 'Cambiar Modo Mantenimiento',
    };
    return map[tool] || tool;
  }

  async executeAuraAdminTool(
    tool: string,
    params: Record<string, any>,
    adminUser: { id: string; username: string; displayName?: string | null },
  ) {
    const adminName = adminUser.displayName || adminUser.username || 'Admin';
    this.logger.log(`⚡ [Aura Admin Tool] Ejecutando "${tool}" solicitado por ${adminName}`);

    switch (tool) {
      case 'send_push_notification': {
        let contentId = String(params.contentId || '').trim();
        let contentType = String(params.contentType || 'peliculas').trim();
        const url = String(params.url || '').trim();

        // Si viene una URL del catálogo pero falta el ID, extraerlo automáticamente
        if (url && !contentId) {
          const parsedUrl = this.catalogSyncService.parseUrlIdAndHint(url);
          if (parsedUrl.itemId) contentId = parsedUrl.itemId;
          if (parsedUrl.typeHint) contentType = parsedUrl.typeHint;
        }

        const destination =
          params.destination ||
          (contentId ? 'content_detail' : url ? 'external_url' : 'app');

        const pushRes = await this.pushService.sendGlobalPush({
          title: String(params.title || 'Audiocinemateca').trim(),
          body: String(params.body || '').trim(),
          destination,
          contentId: contentId || undefined,
          contentType: contentType || undefined,
          url: url || undefined,
          target: params.target || 'all',
          delayMinutes: Number(params.delayMinutes) || 0,
          sentBy: `${adminName} (vía Aura IA)`,
        });

        return {
          success: true,
          tool,
          title: 'Notificación Push Publicada',
          message: `Se envió la notificación "${params.title}" correctamente.`,
          details: pushRes,
        };
      }

      case 'publish_banner': {
        const bannerRes = await this.appConfigService.publishFeaturedBanner({
          enabled: params.enabled ?? true,
          title: String(params.title || 'Destacado en Audiocinemateca').trim(),
          message: String(params.message || '').trim(),
          actionUrl: params.actionUrl || params.url || null,
          actionText: params.actionText || 'Escuchar ahora',
          type: params.type || 'info',
          dismissible: params.dismissible ?? true,
          sendPush: Boolean(params.sendPush),
          optimizePushWithAi: false,
        });

        return {
          success: true,
          tool,
          title: 'Banner Destacado Publicado',
          message: `El banner "${params.title}" ya está activo en el inicio de la aplicación.`,
          details: bannerRes,
        };
      }

      case 'create_exclusive_content': {
        const rawTitle = String(params.title || 'Nueva Producción Exclusiva').trim();
        const baseSlug = rawTitle
          .toLowerCase()
          .normalize('NFD')
          .replace(/[\u0300-\u036f]/g, '')
          .replace(/[^a-z0-9]+/g, '-')
          .replace(/(^-|-$)+/g, '');

        let slug = baseSlug || `exclusivo-${Date.now()}`;
        let counter = 1;
        while (await this.prisma.content.findUnique({ where: { slug } })) {
          slug = `${baseSlug}-${counter++}`;
        }

        const genreRaw = String(params.genreRaw || params.genre || 'Exclusivo, Audiodescripción').trim();
        const catNames = genreRaw
          .split(',')
          .map((g) => g.trim())
          .filter(Boolean);

        const categoryConnectOrCreate = catNames.map((catName) => {
          const catSlug = catName.toLowerCase().replace(/[^a-z0-9]+/g, '-');
          return {
            category: {
              connectOrCreate: {
                where: { name: catName },
                create: { name: catName, slug: catSlug },
              },
            },
          };
        });

        const validTypes = ['MOVIE', 'SERIES', 'DOCUMENTARY', 'SHORT'];
        const contentType = validTypes.includes(String(params.type).toUpperCase())
          ? (String(params.type).toUpperCase() as any)
          : 'MOVIE';

        const created = await this.prisma.content.create({
          data: {
            title: rawTitle,
            slug,
            type: contentType,
            status: params.status === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT',
            isExclusive: true,
            audioDescription: true,
            synopsis:
              String(params.synopsis || '').trim() ||
              `Producción exclusiva de Audiocinemateca con audiodescripción en español.`,
            releaseYear: Number(params.releaseYear) || new Date().getFullYear(),
            country: params.country ? String(params.country).trim() : 'España / Latinoamérica',
            director: params.director ? String(params.director).trim() : undefined,
            cast: params.cast ? String(params.cast).trim() : undefined,
            narrator: params.narrator ? String(params.narrator).trim() : 'Audiocinemateca',
            dubbing: params.dubbing ? String(params.dubbing).trim() : 'Español Latino',
            language: 'es',
            genreRaw,
            categories: {
              create: categoryConnectOrCreate,
            },
            ...(contentType === 'SERIES'
              ? {
                  seasons: {
                    create: [
                      {
                        seasonNumber: 1,
                        title: 'Temporada 1',
                        episodes: {
                          create: [
                            {
                              episodeNumber: 1,
                              title: 'Capítulo 1',
                              audioUrl: '',
                              durationMinutes: 45,
                            },
                          ],
                        },
                      },
                    ],
                  },
                }
              : {}),
          },
        });

        return {
          success: true,
          tool,
          title: 'Ficha Exclusiva Creada',
          message: `Se creó la ficha exclusiva "${created.title}" (${created.status === 'DRAFT' ? 'Borrador listo para subir archivos' : 'Publicada'}).`,
          contentId: created.id,
          editRoute: `/catalog/edit/${created.id}`,
          details: created,
        };
      }

      case 'publish_announcement': {
        const title = String(params.title || 'Comunicado Oficial').trim();
        const content = String(params.content || params.message || '').trim();

        const createdAnn = await this.prisma.announcement.create({
          data: {
            authorId: adminUser.id,
            title,
            content,
          },
        });

        if (params.sendPush !== false) {
          await this.pushService.sendAnnouncementPush(title, content, adminName);
        }

        return {
          success: true,
          tool,
          title: 'Comunicado Oficial Publicado',
          message: `El comunicado "${title}" fue publicado en la comunidad.`,
          details: createdAnn,
        };
      }

      case 'send_global_chat_message': {
        const botUserId = await this.auraAiService.ensureAuraBotUser();
        const text = String(params.message || '').trim();
        const msg = await this.prisma.chatMessage.create({
          data: {
            userId: botUserId,
            content: text,
          },
        });

        return {
          success: true,
          tool,
          title: 'Mensaje Enviado al Chat Global',
          message: `Aura publicó en el Chat Global: "${text}"`,
          details: msg,
        };
      }

      case 'regenerate_collections': {
        const colRes = await this.auraAiService.generateCuratedCollectionsIfNeeded(true);
        return {
          success: true,
          tool,
          title: 'Colecciones Temáticas Regeneradas',
          message: colRes.message || 'Se regeneraron las 3 colecciones curadas con IA.',
          details: colRes,
        };
      }

      case 'recalculate_weekly_ranking': {
        const rankRes = await this.recalculateWeeklyRanking();
        return {
          success: true,
          tool,
          title: 'Ranking Semanal Recalculado',
          message: `Top 10 Semanal actualizado (${rankRes.items?.length || 0} obras).`,
          details: rankRes,
        };
      }

      case 'sync_catalog': {
        const syncRes = await this.catalogSyncService.triggerFullDatabaseSync({
          downloadRemote: true,
          notifyPush: Boolean(params.notifyUsers),
        });
        return {
          success: true,
          tool,
          title: 'Sincronización de Catálogo Iniciada',
          message: 'El catálogo maestro se ha sincronizado con PostgreSQL.',
          details: syncRes,
        };
      }

      case 'toggle_maintenance': {
        const enabled = Boolean(params.enabled);
        const message =
          String(params.message || '').trim() ||
          'Estamos realizando mejoras en la plataforma. Volvemos enseguida.';
        await this.appConfigService.upsertConfig(
          'maintenance_mode',
          { enabled, message, allowAdmins: true },
          'Estado de modo mantenimiento',
        );
        return {
          success: true,
          tool,
          title: enabled ? 'Modo Mantenimiento Activado' : 'Modo Mantenimiento Desactivado',
          message: enabled
            ? 'La plataforma está ahora en modo mantenimiento (administradores tienen acceso).'
            : 'La plataforma está nuevamente abierta a todos los usuarios.',
        };
      }

      default:
        throw new BadRequestException(`Herramienta administrativa desconocida: ${tool}`);
    }
  }
}
