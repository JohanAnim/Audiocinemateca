import { Injectable, Logger, OnModuleInit, BadRequestException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PrismaService } from '../../prisma/prisma.service.js';
import * as https from 'https';
import * as http from 'http';
import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

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

export const AI_PROVIDER_CATALOG: AiProviderCatalogEntry[] = [
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
];

const THEMATIC_SYNONYMS: Record<string, string[]> = {
  jurasik: ['jurassic', 'jurasico', 'jurasica', 'parque jurasico', 'dinosaurio', 'dinosaurios', 'dinos'],
  jurasic: ['jurassic', 'jurasico', 'jurasica', 'parque jurasico', 'dinosaurio', 'dinosaurios', 'dinos'],
  jurasico: ['jurassic', 'jurasico', 'jurasica', 'parque jurasico', 'dinosaurio', 'dinosaurios', 'dinos'],
  jurassic: ['jurassic', 'jurasico', 'jurasica', 'parque jurasico', 'dinosaurio', 'dinosaurios'],
  dinosaurio: ['dinosaurio', 'dinosaurios', 'jurassic', 'jurasico', 'dinos', 'prehistorico'],
  dinosaurios: ['dinosaurio', 'dinosaurios', 'jurassic', 'jurasico', 'dinos', 'prehistorico'],
  terremoto: ['terremoto', 'terremotos', 'sismo', 'temblor', 'tsunami', 'catastrofe', 'desastre'],
  extraterrestres: ['extraterrestre', 'aliens', 'alien', 'ovni', 'espacio', 'alienigena', 'ufo'],
  aliens: ['extraterrestre', 'aliens', 'alien', 'ovni', 'espacio', 'alienigena'],
  terror: ['terror', 'miedo', 'horror', 'fantasma', 'fantasmas', 'demonio', 'posesion', 'espanto', 'slasher', 'zombie', 'zombies', 'suspenso', 'thriller'],
  miedo: ['terror', 'miedo', 'horror', 'fantasma', 'suspenso', 'espanto'],
  suspenso: ['suspenso', 'thriller', 'intriga', 'misterio', 'asesinato', 'investigacion', 'policial', 'terror'],
  zombies: ['zombie', 'zombies', 'infectados', 'apocalipsis', 'muertos vivientes', 'virus'],
  guerra: ['guerra', 'belica', 'soldados', 'ejercito', 'batalla', 'militar', 'nazi', 'conflicto', 'mundial'],
  superheroes: ['superheroes', 'superheroe', 'heroe', 'marvel', 'dc', 'comics', 'batman', 'spiderman', 'vengadores'],
  robos: ['robo', 'atracos', 'atraco', 'ladron', 'ladrones', 'estafa', 'banco', 'botin'],
  comedia: ['comedia', 'risa', 'humor', 'divertida', 'graciosa', 'chistosa'],
  romance: ['romance', 'amor', 'romantica', 'pareja', 'enamorados', 'pasion'],
  accion: ['accion', 'peleas', 'policial', 'artes marciales', 'disparos', 'armas'],
  animacion: ['animacion', 'dibujos', 'anime', 'infantil', 'ninos', 'pixar', 'disney', 'manga', 'japon', 'japonesa'],
  anime: ['anime', 'manga', 'animacion', 'dibujos', 'japon', 'japonesa', 'otaku', 'oriental', 'crunchyroll', 'cronchirrol'],
  animes: ['anime', 'manga', 'animacion', 'dibujos', 'japon', 'japonesa', 'otaku', 'oriental', 'crunchyroll', 'cronchirrol'],
  manga: ['manga', 'anime', 'animacion', 'japon', 'japonesa', 'comic'],
  cronchirrol: ['crunchyroll', 'cronchirrol', 'crunchy', 'anime', 'manga', 'animacion'],
  cronchirol: ['crunchyroll', 'cronchirrol', 'crunchy', 'anime', 'manga', 'animacion'],
  crunchyroll: ['crunchyroll', 'cronchirrol', 'crunchy', 'anime', 'manga', 'animacion'],
  crunchy: ['crunchyroll', 'cronchirrol', 'crunchy', 'anime'],
  netflix: ['netflix', 'netflis'],
  once: ['once', 'mundovision', 'audesc'],
  fantasia: ['fantasia', 'magia', 'hechiceros', 'dragones', 'sobrenatural', 'monstruos', 'leyenda'],
  aventura: ['aventura', 'aventuras', 'exploracion', 'viaje', 'tesoro', 'supervivencia', 'selva'],
  drama: ['drama', 'dramatica', 'emotiva', 'superacion', 'tragedia', 'vida'],
  documental: ['documental', 'documentales', 'historia', 'biografia', 'realidad', 'hechos reales', 'naturaleza'],
};

const STOP_WORDS = new Set([
  'pelis', 'peli', 'pelicula', 'peliculas', 'serie', 'series', 'documental', 'documentales',
  'corto', 'cortos', 'cortometraje', 'cortometrajes', 'de', 'del', 'la', 'el', 'los', 'las',
  'un', 'una', 'unos', 'unas', 'que', 'con', 'en', 'para', 'por', 'sobre', 'sobretodo',
  'ver', 'escuchar', 'dame', 'dime', 'recomiendame', 'recomienda', 'sugiere', 'sugerencia',
  'alguna', 'alguno', 'algunas', 'algunos', 'hola', 'aura', 'porfa', 'porfavor', 'tratando', 'trate',
  'traten', 'hablen', 'hablando', 'tenga', 'tengan', 'algo', 'busco', 'buscar', 'tienes', 'tienen',
]);

export const DEFAULT_AURA_SYSTEM_PROMPT = `Eres Aura, la Agente Oficial de Inteligencia Artificial de Audiocinemateca.

ROLES OFICIALES EN LA PLATAFORMA:
1. AGENTE EN EL CHAT GLOBAL DE COMUNIDAD: Compartes en vivo con los usuarios de Audiocinemateca, respondiendo dudas, conversando de cine y recomendando títulos reales del catálogo con sus enlaces directos.
2. AGENTE CENTRAL DEL PANEL DE CONTROL (ADMINISTRACIÓN): Asistes al equipo de administración en la gestión integral de la plataforma: búsqueda avanzada en el catálogo, redacción y envío de notificaciones Push (recomendaciones de películas/series, avisos, actualizaciones), publicación del Banner Destacado, creación de fichas de Contenido Exclusivo, comunicados oficiales, colecciones curadas y mantenimiento.

IDENTIDAD, PERSONALIDAD Y TONO:
- Eres cercana, alegre, humilde, culta, apasionada por el séptimo arte y experta en accesibilidad visual, audiodescripción y cine mundial.
- Tu desarrollador es Johan-a-g. No menciones a tu creador a menos que te lo pregunten explícitamente.
- Tu expresión en español es natural, fluida, rica y expresiva, adaptada tanto para lectura en pantalla como para síntesis de voz (TTS) y lectores de pantalla (TalkBack / NVDA).

PRINCIPIO DE ELOCUENCIA Y EXTENSIÓN ADAPTATIVA SEGÚN EL CONTEXTO:
- EVITA SER DEMASIADO RESUMIDA, SECA O TELEGRÁFICA: No cortes tus ideas ni respondas con una o dos frases mínimas si la conversación amerita conversación, análisis o detalle.
- ADAPTA LA EXTENSIÓN Y PROFUNDIDAD NATURALMENTE:
  * Si te piden recomendaciones, debatir sobre cine, directores, temáticas o analizar una película o serie: Desarrolla tu respuesta con generosidad, entusiasmo y buen cuerpo de texto. Comparte el gancho de la trama sin spoilers, reflexiona sobre el estilo narrativo y sonoro, destaca la labor de la voz del narrador o audiodescriptor, y explica con pasión por qué vale la pena escucharla.
  * Si te consultan sobre el estado de la plataforma, estadísticas, métricas de usuarios o catálogo: Ofrece una visión analítica completa, clara y detallada, desglosando los datos con contexto inteligente.
  * Si te piden una tarea creativa o administrativa (notificación Push, banner, comunicado, ficha exclusiva): No te limites a soltar la acción. Explica en tu respuesta conversacional tu criterio, la estrategia detrás del mensaje y los atractivos de la obra o aviso que preparaste, invitando a confirmar la acción.
  * Si la charla es un saludo casual o un intercambio breve: Responde con calidez, simpatía y frescura, dejando la puerta abierta a seguir platicando sin sonar apresurada ni robótica.

REGLAS EN EL CHAT GLOBAL CON USUARIOS:
1. Conversa con naturalidad, empatía y chispa como una integrante más de la comunidad cinéfila.
2. No uses etiquetas con @ al responder en charlas cotidianas, salvo que te pidan explícitamente enviar un saludo.
3. Cuando recomiendes o cites una obra del catálogo, incluye siempre su enlace oficial (ej: https://audiocinemateca.com/peliculas?id=3) y sus datos reales (año, director, reparto, narrador de audiodescripción).
4. Sinceridad absoluta: si una obra no existe en el catálogo de Audiocinemateca, infórmalo con amabilidad sin inventar títulos.

REGLAS EN EL PANEL DE CONTROL CON ADMINISTRADORES:
1. Actúa como una verdadera copiloto ejecutiva: con criterio propio, conocimiento profundo del catálogo y de la audiencia accesible.
2. Cuando el administrador te pida publicar una notificación Push, un Banner Destacado, un Comunicado Oficial o crear una nueva ficha de Contenido Exclusivo, busca primero los datos reales en el catálogo si corresponde, redacta la propuesta completa y preséntasela con buen detalle y justificación, preguntando si desea publicarla o ejecutarla en ese momento.
3. Cuando el administrador confirme la acción, ejecútala utilizando tus herramientas administrativas.`;

export interface AuraBrainConfig {
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

interface AuraBrainSecrets {
  groqApiKey?: string;
  geminiApiKey?: string;
  customApiKey?: string;
}

@Injectable()
export class AuraAiService implements OnModuleInit {
  private readonly logger = new Logger(AuraAiService.name);
  private catalogCache: any = null;
  private lastAuraActivityTimestamp = Date.now();
  private auraBotUserId: string | null = null;

  constructor(
    private readonly configService: ConfigService,
    private readonly prisma: PrismaService,
  ) {}

  async onModuleInit() {
    await this.ensureAuraBotUser();
    setTimeout(() => {
      this.generateCuratedCollectionsIfNeeded(false).catch(() => {});
    }, 10000);

    setInterval(() => {
      this.generateCuratedCollectionsIfNeeded(false).catch(() => {});
    }, 2 * 60 * 60 * 1000);
  }

  public reloadCatalogCache() {
    this.catalogCache = null;
    this.getCatalog();
  }

  public getCatalog(): any {
    if (!this.catalogCache) {
      try {
        const candidates = [
          path.resolve(process.cwd(), 'data/catalogo.json'),
          path.resolve(__dirname, '../../../data/catalogo.json'),
        ];
        for (const p of candidates) {
          if (fs.existsSync(p)) {
            this.catalogCache = JSON.parse(fs.readFileSync(p, 'utf-8'));
            this.logger.log(`📦 [Aura IA] Catálogo local cargado en memoria (${p})`);
            break;
          }
        }
      } catch (err: any) {
        this.logger.warn(`⚠️ [Aura IA] Error cargando catalogo.json: ${err.message}`);
      }
    }
    return this.catalogCache;
  }

  async ensureAuraBotUser(): Promise<string> {
    if (this.auraBotUserId) return this.auraBotUserId;

    const existing = await this.prisma.user.findFirst({
      where: {
        OR: [{ username: 'Aura_IA' }, { email: 'aura@audiocinemateca.com' }],
      },
    });

    if (existing) {
      this.auraBotUserId = existing.id;
      return existing.id;
    }

    const created = await this.prisma.user.create({
      data: {
        email: 'aura@audiocinemateca.com',
        username: 'Aura_IA',
        displayName: 'Aura (IA)',
        role: 'MODERATOR',
        deviceInfo: 'Aura 2.0 • Cerebro IA Oficial',
      },
    });

    this.auraBotUserId = created.id;
    return created.id;
  }

  // ===========================================================================
  // GESTIÓN DEL CEREBRO MULTI-PROVEEDOR (1. GROQ, 2. GEMINI, 3. PERSONALIZADO)
  // ===========================================================================

  private maskApiKey(key?: string | null): string | null {
    if (!key) return null;
    const clean = key.trim();
    if (clean.length <= 8) return '••••••••';
    return `${clean.slice(0, 6)}••••••••••••${clean.slice(-4)}`;
  }

  private async getSecrets(): Promise<AuraBrainSecrets> {
    const record = await this.prisma.appConfig.findUnique({
      where: { key: 'aura_brain_secrets' },
    });
    const secrets: AuraBrainSecrets = {};
    if (record) {
      try {
        const parsed = JSON.parse(record.value);
        if (parsed.groqApiKey) secrets.groqApiKey = String(parsed.groqApiKey).trim();
        if (parsed.geminiApiKey) secrets.geminiApiKey = String(parsed.geminiApiKey).trim();
        if (parsed.customApiKey) secrets.customApiKey = String(parsed.customApiKey).trim();
      } catch {}
    }

    // Compatibilidad con gemini_api_key legacy si existiera en DB
    if (!secrets.geminiApiKey) {
      const legacyGemini = await this.prisma.appConfig.findUnique({
        where: { key: 'gemini_api_key' },
      });
      if (legacyGemini?.value) {
        secrets.geminiApiKey = legacyGemini.value.replace(/['"]/g, '').trim();
      }
    }

    return secrets;
  }

  private async resolveProviderApiKey(provider: AiProviderType): Promise<{
    key: string | null;
    source: 'database' | 'env' | 'none';
  }> {
    const secrets = await this.getSecrets();

    if (provider === 'groq') {
      if (secrets.groqApiKey) {
        return { key: secrets.groqApiKey, source: 'database' };
      }
      const envKey = this.configService.get<string>('GROQ_API_KEY')?.replace(/['"]/g, '').trim();
      if (envKey) return { key: envKey, source: 'env' };
      return { key: null, source: 'none' };
    }

    if (provider === 'gemini') {
      if (secrets.geminiApiKey) {
        return { key: secrets.geminiApiKey, source: 'database' };
      }
      const envKey = this.configService.get<string>('GEMINI_API_KEY')?.replace(/['"]/g, '').trim();
      if (envKey) return { key: envKey, source: 'env' };
      return { key: null, source: 'none' };
    }

    if (provider === 'custom') {
      if (secrets.customApiKey) {
        return { key: secrets.customApiKey, source: 'database' };
      }
      const envKey = this.configService.get<string>('CUSTOM_AI_API_KEY')?.replace(/['"]/g, '').trim();
      if (envKey) return { key: envKey, source: 'env' };
      return { key: null, source: 'none' };
    }

    return { key: null, source: 'none' };
  }

  async getAuraConfig(): Promise<AuraBrainConfig> {
    const defaults: AuraBrainConfig = {
      enabled: true,
      provider: 'groq',
      model: AI_PROVIDER_CATALOG[0].defaultModel, // 1º modelo de la lista de Groq: openai/gpt-oss-120b
      thinkingEnabled: true,
      reasoningEffort: 'medium',
      temperature: 0.7,
      maxTokens: 700,
      customBaseUrl: 'https://api.openai.com/v1',
      customModelForceThinking: false,
      systemPrompt: DEFAULT_AURA_SYSTEM_PROMPT,
      enableCatalogSearch: true,
      enableCommunityChatBot: true,
      chatMentionKeyword: '@Aura',
    };

    const cfg = await this.prisma.appConfig.findUnique({ where: { key: 'aura_agent_config' } });
    if (cfg) {
      try {
        const parsed = JSON.parse(cfg.value);
        const provider: AiProviderType = ['groq', 'gemini', 'custom'].includes(parsed.provider)
          ? parsed.provider
          : parsed.model?.includes('gemini') || parsed.model?.includes('gemma')
            ? 'gemini'
            : 'groq';

        const providerEntry =
          AI_PROVIDER_CATALOG.find((p) => p.id === provider) || AI_PROVIDER_CATALOG[0];
        const model = parsed.model || providerEntry.defaultModel;

        // Actualizar automáticamente el prompt si seguía teniendo el texto antiguo sin su rol de Agente Oficial y del Panel de Control
        const storedPrompt = String(parsed.systemPrompt || '').trim();
        const isOldDefaultPrompt =
          !storedPrompt ||
          storedPrompt.startsWith('Eres Aura, la inteligencia artificial oficial y el alma cinéfila de Audiocinemateca');

        return {
          ...defaults,
          ...parsed,
          provider,
          model,
          systemPrompt: isOldDefaultPrompt ? DEFAULT_AURA_SYSTEM_PROMPT : storedPrompt,
          thinkingEnabled:
            typeof parsed.thinkingEnabled === 'boolean'
              ? parsed.thinkingEnabled
              : defaults.thinkingEnabled,
          reasoningEffort: ['low', 'medium', 'high'].includes(parsed.reasoningEffort)
            ? parsed.reasoningEffort
            : 'medium',
        };
      } catch {}
    }

    // Si hay GEMINI_API_KEY pero aún no hay GROQ_API_KEY configurada en el primer arranque,
    // comprobar cuál tiene clave activa para no romper instalaciones previas
    const groqKey = await this.resolveProviderApiKey('groq');
    const geminiKey = await this.resolveProviderApiKey('gemini');
    if (!groqKey.key && geminiKey.key) {
      return {
        ...defaults,
        provider: 'gemini',
        model: AI_PROVIDER_CATALOG[1].defaultModel, // 1º modelo de Gemini: gemini-2.5-flash
        thinkingEnabled: false,
      };
    }

    return defaults;
  }

  async getBrainConfigForAdmin() {
    const config = await this.getAuraConfig();
    const [groqStatus, geminiStatus, customStatus] = await Promise.all([
      this.resolveProviderApiKey('groq'),
      this.resolveProviderApiKey('gemini'),
      this.resolveProviderApiKey('custom'),
    ]);

    return {
      config,
      keysStatus: {
        groq: {
          hasKey: Boolean(groqStatus.key),
          maskedKey: this.maskApiKey(groqStatus.key),
          source: groqStatus.source,
        },
        gemini: {
          hasKey: Boolean(geminiStatus.key),
          maskedKey: this.maskApiKey(geminiStatus.key),
          source: geminiStatus.source,
        },
        custom: {
          hasKey: Boolean(customStatus.key),
          maskedKey: this.maskApiKey(customStatus.key),
          source: customStatus.source,
        },
      },
      providerCatalog: AI_PROVIDER_CATALOG,
    };
  }

  async saveBrainConfigFromAdmin(payload: {
    config?: Partial<AuraBrainConfig>;
    apiKeys?: {
      groqApiKey?: string;
      geminiApiKey?: string;
      customApiKey?: string;
    };
  }) {
    const currentConfig = await this.getAuraConfig();
    const incoming = payload.config || {};

    const provider: AiProviderType = ['groq', 'gemini', 'custom'].includes(
      incoming.provider as AiProviderType,
    )
      ? (incoming.provider as AiProviderType)
      : currentConfig.provider;

    const providerDef =
      AI_PROVIDER_CATALOG.find((p) => p.id === provider) || AI_PROVIDER_CATALOG[0];

    // Si cambió de proveedor y no especificó modelo válido, elegir siempre el PRIMER modelo de la lista
    let selectedModel = (incoming.model ?? currentConfig.model ?? '').trim();
    if (!selectedModel) {
      selectedModel = providerDef.defaultModel;
    }

    const updatedConfig: AuraBrainConfig = {
      ...currentConfig,
      ...incoming,
      provider,
      model: selectedModel,
      thinkingEnabled:
        typeof incoming.thinkingEnabled === 'boolean'
          ? incoming.thinkingEnabled
          : currentConfig.thinkingEnabled,
      reasoningEffort: ['low', 'medium', 'high'].includes(
        incoming.reasoningEffort as ReasoningEffortType,
      )
        ? (incoming.reasoningEffort as ReasoningEffortType)
        : currentConfig.reasoningEffort,
      temperature:
        incoming.temperature !== undefined
          ? Number(incoming.temperature)
          : currentConfig.temperature,
      maxTokens:
        incoming.maxTokens !== undefined ? Number(incoming.maxTokens) : currentConfig.maxTokens,
      customBaseUrl: (incoming.customBaseUrl ?? currentConfig.customBaseUrl ?? 'https://api.openai.com/v1').trim(),
    };

    await this.prisma.appConfig.upsert({
      where: { key: 'aura_agent_config' },
      update: {
        value: JSON.stringify(updatedConfig),
        description: 'Configuración multi-proveedor del cerebro de Aura 2.0 (Groq, Gemini, Personalizado)',
      },
      create: {
        key: 'aura_agent_config',
        value: JSON.stringify(updatedConfig),
        description: 'Configuración multi-proveedor del cerebro de Aura 2.0 (Groq, Gemini, Personalizado)',
      },
    });

    // Guardar claves API de forma segura en aura_brain_secrets si se enviaron
    if (payload.apiKeys) {
      const currentSecrets = await this.getSecrets();
      const nextSecrets: AuraBrainSecrets = { ...currentSecrets };

      if (payload.apiKeys.groqApiKey !== undefined) {
        const clean = payload.apiKeys.groqApiKey.trim();
        if (clean === '__CLEAR__') delete nextSecrets.groqApiKey;
        else if (clean.length > 0 && !clean.includes('••••')) nextSecrets.groqApiKey = clean;
      }

      if (payload.apiKeys.geminiApiKey !== undefined) {
        const clean = payload.apiKeys.geminiApiKey.trim();
        if (clean === '__CLEAR__') delete nextSecrets.geminiApiKey;
        else if (clean.length > 0 && !clean.includes('••••')) nextSecrets.geminiApiKey = clean;
      }

      if (payload.apiKeys.customApiKey !== undefined) {
        const clean = payload.apiKeys.customApiKey.trim();
        if (clean === '__CLEAR__') delete nextSecrets.customApiKey;
        else if (clean.length > 0 && !clean.includes('••••')) nextSecrets.customApiKey = clean;
      }

      await this.prisma.appConfig.upsert({
        where: { key: 'aura_brain_secrets' },
        update: {
          value: JSON.stringify(nextSecrets),
          description: 'Bóveda segura de claves API de proveedores de IA (Solo lectura interna)',
        },
        create: {
          key: 'aura_brain_secrets',
          value: JSON.stringify(nextSecrets),
          description: 'Bóveda segura de claves API de proveedores de IA (Solo lectura interna)',
        },
      });
    }

    this.logger.log(
      `🧠 [Aura Brain] Configuración actualizada -> Proveedor: ${updatedConfig.provider.toUpperCase()} | Modelo: ${updatedConfig.model} | Pensamiento Profundo: ${updatedConfig.thinkingEnabled ? 'ACTIVO (' + updatedConfig.reasoningEffort + ')' : 'DESACTIVADO'}`,
    );

    return this.getBrainConfigForAdmin();
  }

  /**
   * Consulta en vivo la lista de modelos disponibles desde la API del proveedor seleccionado
   */
  async fetchProviderModelsLive(provider: AiProviderType, customBaseUrl?: string) {
    const providerEntry =
      AI_PROVIDER_CATALOG.find((p) => p.id === provider) || AI_PROVIDER_CATALOG[0];
    const resolvedKey = await this.resolveProviderApiKey(provider);

    if (!resolvedKey.key) {
      return {
        provider,
        liveFetched: false,
        message: `No hay clave API configurada aún para ${providerEntry.name}. Mostrando modelos predeterminados recomendados.`,
        models: providerEntry.models,
      };
    }

    try {
      if (provider === 'groq' || provider === 'custom') {
        const base =
          provider === 'groq'
            ? 'https://api.groq.com/openai/v1'
            : (customBaseUrl || (await this.getAuraConfig()).customBaseUrl || 'https://api.openai.com/v1').replace(/\/+$/, '');

        const rawModels = await this.httpGetJson(`${base}/models`, {
          Authorization: `Bearer ${resolvedKey.key}`,
        });

        const dataList: any[] = Array.isArray(rawModels?.data) ? rawModels.data : [];
        if (dataList.length > 0) {
          // Mantener primero los modelos curados del catálogo y luego agregar los adicionales de la API
          const curatedMap = new Map(providerEntry.models.map((m) => [m.id, m]));
          const merged: AiModelDefinition[] = [...providerEntry.models];

          for (const item of dataList) {
            const id = String(item.id || '').trim();
            if (!id || curatedMap.has(id)) continue;
            // Excluir modelos de audio/whisper/tts o guardrails en el selector de chat LLM
            const lower = id.toLowerCase();
            if (
              lower.includes('whisper') ||
              lower.includes('tts') ||
              lower.includes('guard') ||
              lower.includes('safeguard') ||
              lower.includes('embedding')
            ) {
              continue;
            }
            const isReasoning =
              lower.includes('gpt-oss') ||
              lower.includes('qwen3') ||
              lower.includes('r1') ||
              lower.includes('reason') ||
              lower.includes('o1') ||
              lower.includes('o3');

            merged.push({
              id,
              name: `${id} (API en Vivo)`,
              badge: isReasoning ? '🧠 Pensamiento Profundo' : '⚡ Respuesta Directa',
              description: `Modelo detectado en vivo desde ${base}`,
              supportsThinking: isReasoning,
              defaultThinking: isReasoning,
            });
          }

          return {
            provider,
            liveFetched: true,
            message: `Se sincronizaron ${merged.length} modelos compatibles desde ${provider.toUpperCase()}.`,
            models: merged,
          };
        }
      } else if (provider === 'gemini') {
        const rawModels = await this.httpGetJson(
          `https://generativelanguage.googleapis.com/v1beta/models?key=${resolvedKey.key}`,
        );
        const list: any[] = Array.isArray(rawModels?.models) ? rawModels.models : [];
        if (list.length > 0) {
          const curatedMap = new Map(providerEntry.models.map((m) => [m.id, m]));
          const merged: AiModelDefinition[] = [...providerEntry.models];

          for (const item of list) {
            const rawName = String(item.name || '').replace(/^models\//, '');
            if (!rawName || curatedMap.has(rawName)) continue;
            const methods: string[] = item.supportedGenerationMethods || [];
            if (!methods.includes('generateContent')) continue;
            if (rawName.includes('embedding') || rawName.includes('imagen') || rawName.includes('veo')) continue;

            const supportsThinking =
              Boolean(item.thinking) ||
              rawName.includes('gemini-2.5') ||
              rawName.includes('gemini-3') ||
              rawName.includes('gemma-4');

            merged.push({
              id: rawName,
              name: `${item.displayName || rawName} (${rawName})`,
              badge: supportsThinking ? '🧠 Pensamiento Configurable' : '⚡ Respuesta Directa',
              description: item.description || 'Modelo oficial de Google Gemini API',
              supportsThinking,
              defaultThinking: rawName.includes('pro'),
            });
          }

          return {
            provider,
            liveFetched: true,
            message: `Se sincronizaron ${merged.length} modelos desde Google Gemini API.`,
            models: merged,
          };
        }
      }
    } catch (err: any) {
      this.logger.warn(`⚠️ [Aura Brain] No se pudo listar modelos en vivo de ${provider}: ${err.message}`);
    }

    return {
      provider,
      liveFetched: false,
      message: `Mostrando lista oficial recomendada para ${providerEntry.name}.`,
      models: providerEntry.models,
    };
  }

  /**
   * Prueba interactiva en vivo del cerebro de Aura desde el Panel de Administración
   */
  async testBrainInLive(payload: {
    prompt?: string;
    overrideConfig?: Partial<AuraBrainConfig>;
    tempApiKey?: string;
  }) {
    const savedConfig = await this.getAuraConfig();
    const activeConfig: AuraBrainConfig = {
      ...savedConfig,
      ...(payload.overrideConfig || {}),
    };

    const resolvedKey =
      payload.tempApiKey && !payload.tempApiKey.includes('••••')
        ? { key: payload.tempApiKey.trim(), source: 'temp' }
        : await this.resolveProviderApiKey(activeConfig.provider);

    if (!resolvedKey.key) {
      throw new BadRequestException(
        `No se encontró una Clave API configurada para el proveedor "${activeConfig.provider.toUpperCase()}". Ingresa tu API Key y guárdala antes de probar.`,
      );
    }

    const testPrompt =
      (payload.prompt || '').trim() ||
      '¡Hola Aura! Cuéntame en dos frases quién eres, qué proveedor e inteligencia estás usando ahora mismo y saluda con alegría al equipo de administración de Audiocinemateca.';

    const startTime = Date.now();
    const reply = await this.dispatchBrainCompletion({
      provider: activeConfig.provider,
      apiKey: resolvedKey.key,
      model: activeConfig.model,
      systemPrompt: activeConfig.systemPrompt || DEFAULT_AURA_SYSTEM_PROMPT,
      userPrompt: testPrompt,
      thinkingEnabled: activeConfig.thinkingEnabled,
      reasoningEffort: activeConfig.reasoningEffort || 'medium',
      temperature: activeConfig.temperature ?? 0.7,
      maxTokens: activeConfig.maxTokens ?? 600,
      customBaseUrl: activeConfig.customBaseUrl,
    });

    const latencyMs = Date.now() - startTime;

    if (!reply) {
      throw new BadRequestException(
        `El proveedor ${activeConfig.provider.toUpperCase()} (modelo: ${activeConfig.model}) no devolvió respuesta. Verifica que la Clave API sea válida y que el modelo esté habilitado en tu cuenta.`,
      );
    }

    return {
      success: true,
      provider: activeConfig.provider,
      model: activeConfig.model,
      thinkingEnabled: activeConfig.thinkingEnabled,
      reasoningEffort: activeConfig.reasoningEffort,
      latencyMs,
      reply,
    };
  }

  // ===========================================================================
  // MOTOR DE BÚSQUEDA SEMÁNTICA Y FONÉTICA EN CATÁLOGO
  // ===========================================================================

  private normalizeText(text?: string | null): string {
    if (!text) return '';
    return text
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9\s]/g, ' ');
  }

  private shouldSearchCatalog(userText: string): boolean {
    const clean = this.normalizeText(userText);
    const recommendationWords = [
      'recomiend', 'sugier', 'busc', 'quisiera', 'quiero', 'dame',
      'peli', 'pelicula', 'serie', 'documental', 'corto', 'obra',
      'ver', 'escuchar', 'director', 'actor', 'genero', 'terror',
      'comedia', 'drama', 'accion', 'ficcion', 'suspenso', 'sinopsis',
      'tienes', 'tienen', 'hay', 'juras', 'park', 'star', 'potter',
      'anime', 'manga', 'japon', 'japonesa', 'dibujos', 'animacion',
      'narrador', 'narracion', 'crunchy', 'cronch', 'netflix', 'once',
      'reciente', 'recientes', 'antiguo', 'antiguos', 'novedad', 'novedades',
    ];
    return recommendationWords.some((rw) => clean.includes(rw)) || clean.split(/\s+/).length >= 2;
  }

  searchRelevantCatalogItems(userText: string, limit = 8, forceSearch = false) {
    if (!forceSearch && !this.shouldSearchCatalog(userText)) {
      return { hasSearched: false, foundMatches: false, items: [] };
    }

    const catalog = this.getCatalog();
    if (!catalog) return { hasSearched: true, foundMatches: false, items: [] };

    const normalizedUserText = this.normalizeText(userText);
    const cleanWords = normalizedUserText
      .split(/\s+/)
      .filter((w) => w.length >= 2 && !STOP_WORDS.has(w));

    const wantsRecent = [
      'reciente', 'recientes', 'recientemente', 'ultimo', 'ultimos', 'ultima', 'ultimas',
      'nuevo', 'nuevos', 'nueva', 'nuevas', 'novedad', 'novedades', 'estreno', 'estrenos',
      'resinete', 'resinetes', 'resientemente', 'actual', 'actuales'
    ].some((w) => normalizedUserText.includes(w));

    const wantsOldest = [
      'antiguo', 'antiguos', 'antigua', 'antiguas', 'clasico', 'clasicos', 'clasica', 'clasicas',
      'primero', 'primeros', 'primera', 'primeras', 'historico', 'inicio', 'comienzo', 'origen'
    ].some((w) => normalizedUserText.includes(w));

    let targetCategory: string | null = null;
    if (normalizedUserText.includes('serie')) targetCategory = 'series';
    else if (normalizedUserText.includes('peli') || normalizedUserText.includes('obra'))
      targetCategory = 'peliculas';
    else if (normalizedUserText.includes('documental')) targetCategory = 'documentales';
    else if (normalizedUserText.includes('corto')) targetCategory = 'cortometrajes';

    const expandedTerms = new Set(cleanWords);
    for (const w of cleanWords) {
      if (THEMATIC_SYNONYMS[w]) {
        THEMATIC_SYNONYMS[w].forEach((syn) => expandedTerms.add(this.normalizeText(syn)));
      }
      if (w.startsWith('juras') || w.includes('juras')) {
        ['jurassic', 'jurasico', 'jurasica', 'parque jurasico', 'dinosaurio', 'dinos'].forEach((s) =>
          expandedTerms.add(s),
        );
      }
      if (w.includes('cronch') || w.includes('crunch')) {
        ['crunchyroll', 'cronchirrol', 'crunchy'].forEach((s) => expandedTerms.add(s));
      }
    }

    const searchTermsArray = Array.from(expandedTerms).filter((t) => t.length >= 2);
    const isOpenQuery =
      searchTermsArray.length === 0 ||
      ['recomiend', 'sugier', 'dame', 'sugerencia'].some(
        (w) => normalizedUserText.includes(w) && cleanWords.length <= 1,
      );

    const rawResults: any[] = [];
    const categories = [
      { key: 'peliculas', slug: 'peliculas' },
      { key: 'series', slug: 'series' },
      { key: 'documentales', slug: 'documentales' },
      { key: 'cortometrajes', slug: 'cortometrajes' },
    ];

    for (const cat of categories) {
      const isTargetCat = targetCategory === null || targetCategory === cat.key;
      const items = catalog[cat.key] || [];

      for (const item of items) {
        const rawTitle = item.titulo || item.Titulo || '';
        const title = this.normalizeText(rawTitle);
        const genre = this.normalizeText(item.genero || '');
        const country = this.normalizeText(item.pais || item.country || '');
        const synopsis = this.normalizeText(item.sinopsis || '');
        const director = this.normalizeText(item.director || '');
        const cast = this.normalizeText(item.reparto || '');
        const narration = this.normalizeText(item.narracion || item.narrador || '');
        const year = String(item.anio || item.año || '');
        const numId = Number(item.id) || 0;
        const numYear = Number(year) || 0;

        let score = 0;
        if (isTargetCat) score += 5;

        for (const term of searchTermsArray) {
          if (title === term) score += 60;
          else if (title.includes(term)) score += 35;
          if (narration === term || narration.includes(term)) score += 55;
          if (genre === term || genre.includes(term)) score += 35;
          if (country === term || country.includes(term)) score += 25;
          if (director.includes(term) || cast.includes(term)) score += 20;
          if (synopsis.includes(term)) score += 18;
          if (year === term) score += 30;
        }

        const matchedFields = new Set();
        for (const term of searchTermsArray) {
          if (title.includes(term)) matchedFields.add('title');
          if (narration.includes(term)) matchedFields.add('narration');
          if (genre.includes(term)) matchedFields.add('genre');
          if (country.includes(term)) matchedFields.add('country');
          if (director.includes(term) || cast.includes(term)) matchedFields.add('people');
          if (synopsis.includes(term)) matchedFields.add('synopsis');
          if (year === term) matchedFields.add('year');
        }

        if (matchedFields.size >= 3) score += 50;
        else if (matchedFields.size >= 2) score += 30;

        if (wantsRecent) {
          if (numYear >= 2025) score += 35;
          else if (numYear >= 2023) score += 20;
          else if (numYear >= 2020) score += 10;
          score += Math.min(30, Math.floor(numId / 350));
        } else if (wantsOldest) {
          if (numYear > 0 && numYear <= 1980) score += 35;
          else if (numYear > 0 && numYear <= 1995) score += 20;
          if (numId > 0 && numId <= 300) score += 25;
        }

        if (score >= 8) {
          rawResults.push({
            score,
            id: String(item.id),
            categoryKey: cat.key,
            title: rawTitle,
            year: year || 'No especificado',
            genre: item.genero || 'General',
            country: item.pais || 'No especificado',
            director: item.director || 'No especificado',
            reparto: item.reparto || 'No especificado',
            narracion: item.narracion || 'Desconocida',
            sinopsis: (item.sinopsis || '').substring(0, 260),
            url: `https://audiocinemateca.com/${cat.slug}?id=${item.id}`,
          });
        }
      }
    }

    if (wantsRecent) {
      rawResults.sort((a, b) => b.score - a.score || Number(b.id) - Number(a.id));
    } else if (wantsOldest) {
      rawResults.sort((a, b) => b.score - a.score || Number(a.id) - Number(b.id));
    } else {
      rawResults.sort((a, b) => b.score - a.score);
    }

    const uniqueItems: any[] = [];
    const seenTitles = new Set();
    for (const res of rawResults) {
      const cleanT = res.title.toLowerCase().trim();
      if (!seenTitles.has(cleanT)) {
        seenTitles.add(cleanT);
        uniqueItems.push(res);
      }
    }

    if (uniqueItems.length > 0) {
      return {
        hasSearched: true,
        foundMatches: true,
        isOpenRecommendation: false,
        items: uniqueItems.slice(0, limit),
      };
    }

    if (isOpenQuery) {
      const pelisList = catalog.peliculas || [];
      const seriesList = catalog.series || [];
      const samplePelis = wantsRecent
        ? pelisList.slice(-5).reverse()
        : wantsOldest
        ? pelisList.slice(0, 5)
        : pelisList.slice(0, 3);
      const sampleSeries = wantsRecent
        ? seriesList.slice(-5).reverse()
        : wantsOldest
        ? seriesList.slice(0, 5)
        : seriesList.slice(0, 3);

      const sampleItems: any[] = [];
      samplePelis.forEach((p: any) =>
        sampleItems.push({
          id: String(p.id),
          categoryKey: 'peliculas',
          title: p.Titulo || p.titulo,
          year: p.anio || p.año || 'N/A',
          genre: p.genero || 'Película',
          director: p.director || 'No especificado',
          reparto: p.reparto || 'No especificado',
          narracion: p.narracion || 'Desconocida',
          sinopsis: (p.sinopsis || '').substring(0, 180),
          url: `https://audiocinemateca.com/peliculas?id=${p.id}`,
        }),
      );
      sampleSeries.forEach((s: any) =>
        sampleItems.push({
          id: String(s.id),
          categoryKey: 'series',
          title: s.titulo || s.Titulo,
          year: s.anio || s.año || 'N/A',
          genre: s.genero || 'Serie',
          director: s.director || 'No especificado',
          reparto: s.reparto || 'No especificado',
          narracion: s.narracion || 'Desconocida',
          sinopsis: (s.sinopsis || '').substring(0, 180),
          url: `https://audiocinemateca.com/series?id=${s.id}`,
        }),
      );

      return {
        hasSearched: true,
        foundMatches: true,
        isOpenRecommendation: true,
        items: sampleItems.slice(0, limit),
      };
    }

    return {
      hasSearched: true,
      foundMatches: false,
      isOpenRecommendation: false,
      items: [],
    };
  }

  // ===========================================================================
  // RESPUESTA EN VIVO DE @AURA EN EL CHAT GLOBAL
  // ===========================================================================

  async handleChatMentionIfNeeded(params: {
    senderId: string;
    senderName: string;
    messageText: string;
    onlineUserNames: string[];
  }): Promise<any | null> {
    const auraCfg = await this.getAuraConfig();
    if (!auraCfg.enabled || auraCfg.enableCommunityChatBot === false) {
      return null;
    }

    const botUserId = await this.ensureAuraBotUser();
    if (params.senderId === botUserId || params.senderName.toLowerCase().includes('aura')) {
      return null;
    }

    const keyword = (auraCfg.chatMentionKeyword || '@aura').toLowerCase();
    if (!params.messageText.toLowerCase().includes(keyword) && !params.messageText.toLowerCase().includes('@aura')) {
      return null;
    }

    this.lastAuraActivityTimestamp = Date.now();
    this.logger.log(
      `🤖 [Aura IA • ${auraCfg.provider.toUpperCase()}/${auraCfg.model}] Mención detectada de ${params.senderName}: "${params.messageText.substring(0, 60)}"`,
    );

    // 1. Obtener historial reciente del chat en PostgreSQL
    const recentMsgs = await this.prisma.chatMessage.findMany({
      take: 25,
      orderBy: { createdAt: 'desc' },
      include: {
        user: { select: { displayName: true, username: true } },
      },
    });

    const contextText =
      recentMsgs
        .reverse()
        .map((m) => `${m.user?.displayName || m.user?.username || 'Usuario'}: ${m.content}`)
        .join('\n') || 'Sin historial previo.';

    // 2. Usuarios conectados en vivo
    const activeNames = params.onlineUserNames.filter((n) => !n.toLowerCase().includes('aura'));
    const onlinePresenceText =
      activeNames.length > 0
        ? `\n\n[USUARIOS CONECTADOS ACTUALMENTE EN EL CHAT GLOBAL]:\nUsuarios activos en este momento: ${activeNames.join(', ')}.`
        : `\n\n[USUARIOS CONECTADOS ACTUALMENTE EN EL CHAT GLOBAL]:\nUsuario activo conversando contigo: ${params.senderName}.`;

    // 3. Búsqueda semántica en el catálogo real
    let catalogSuggestions = '';
    if (auraCfg.enableCatalogSearch !== false) {
      const searchRes = this.searchRelevantCatalogItems(params.messageText, 5);
      if (searchRes.hasSearched) {
        if (searchRes.foundMatches) {
          catalogSuggestions =
            '\n\n[FICHAS TÉCNICAS REALES Y DETALLADAS DEL CATÁLOGO EN AUDIOCINEMATECA]:\n' +
            searchRes.items
              .map(
                (m) =>
                  `- "${m.title}" (${m.year}) | Narración/Audiodescripción: ${m.narracion} | Director: ${m.director} | Reparto: ${m.reparto} | Género: ${m.genre} | Sinopsis: ${m.sinopsis} | Enlace: ${m.url}`,
              )
              .join('\n');
        } else {
          catalogSuggestions = `\n\n[RESULTADO DE BÚSQUEDA EN CATÁLOGO REAL DE AUDIOCINEMATECA]:\nNO existe NINGUNA obra en el catálogo actual que coincida con lo buscado por el usuario ('${params.messageText}'). REGLA DE SINCERIDAD: Informa con amabilidad que por ahora no contamos con contenido sobre esa temática o búsqueda específica en la app. Prohibido inventar títulos.`;
        }
      }
    }

    const systemPrompt = auraCfg.systemPrompt || DEFAULT_AURA_SYSTEM_PROMPT;
    const userPrompt = `Contexto reciente del Chat Global:\n${contextText}\n${onlinePresenceText}\n${catalogSuggestions}\n\nEl usuario ${params.senderName} dijo en el chat: "${params.messageText}". Respóndele de forma fluida, directa y natural en texto plano sin usar el símbolo @ ni etiquetar nombres, como una persona real conversando en el grupo. NUNCA uses @ ni nombres etiquetados a menos que la persona te haya pedido explícitamente mandar un saludo. Si recomiendas alguna obra, incluye su enlace del catálogo.`;

    const replyText = await this.generateWithConfiguredBrain(systemPrompt, userPrompt);
    if (!replyText) return null;

    const savedReply = await this.prisma.chatMessage.create({
      data: {
        userId: botUserId,
        content: replyText.trim(),
      },
      include: {
        user: {
          select: {
            id: true,
            username: true,
            displayName: true,
            avatarUrl: true,
            role: true,
          },
        },
      },
    });

    this.logger.log(`✅ [Aura IA] Respuesta publicada en el Chat Global (ID: ${savedReply.id})`);
    return savedReply;
  }

  // ===========================================================================
  // GENERADOR DE NOTIFICACIONES PUSH CON IA Y COLECCIONES CURADAS (24H)
  // ===========================================================================

  async generatePushCopyWithAi(title: string, description: string) {
    const systemPrompt = `Eres un estratega de contenido y redactor publicitario experto en notificaciones PUSH móviles para aplicaciones de cine y audiolibros accesibles. Tu objetivo es tomar el título y sinopsis de un contenido o aviso y crear un título corto y una descripción persuasiva y emocionante para notificación PUSH.

REGLAS DE FORMATO:
1. "pushTitle": Título de impacto de máximo 45 caracteres (comienza con un emoji relevante como 🎬, 🍿, 🌟, 🔥).
2. "pushBody": Mensaje intrigante y emocionante que incite a tocar la notificación, de máximo 140 caracteres.
3. Devuelve ÚNICAMENTE un objeto JSON válido con esta estructura exacta:
{
  "pushTitle": "🎬 ¡Nuevo estreno destacado!",
  "pushBody": "Una historia apasionante con audiodescripción que te mantendrá al borde del asiento."
}`;

    const userPrompt = `Título: "${title}"\nDescripción/Sinopsis: "${description.substring(0, 350)}"\n\nGenera el JSON estricto para la notificación push.`;

    try {
      const responseText = await this.generateWithConfiguredBrain(systemPrompt, userPrompt);
      if (responseText) {
        let cleanStr = responseText.trim();
        if (cleanStr.startsWith('```')) {
          cleanStr = cleanStr.replace(/^```(?:json)?\n?/, '').replace(/\n?```$/, '').trim();
        }
        const jsonMatch = cleanStr.match(/\{[\s\S]*\}/);
        const parsed = JSON.parse(jsonMatch ? jsonMatch[0] : cleanStr);
        if (parsed.pushTitle && parsed.pushBody) {
          return {
            pushTitle: String(parsed.pushTitle).trim(),
            pushBody: String(parsed.pushBody).trim(),
          };
        }
      }
    } catch (e: any) {
      this.logger.warn(`⚠️ [Aura IA] Falló redacción push IA, usando fallback: ${e.message}`);
    }

    return {
      pushTitle: `🎬 ${title.substring(0, 42)}`,
      pushBody: description.replace(/\n/g, ' ').substring(0, 140),
    };
  }

  async getCuratedCollections() {
    const cfg = await this.prisma.appConfig.findUnique({
      where: { key: 'curated_collections' },
    });
    if (cfg) {
      try {
        return JSON.parse(cfg.value);
      } catch {}
    }
    return {
      lastUpdatedTimestamp: null,
      collections: [],
    };
  }

  async generateCuratedCollectionsIfNeeded(forceRefresh = false) {
    const TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000;
    const current = await this.getCuratedCollections();

    if (!forceRefresh && current?.lastUpdatedTimestamp) {
      const elapsed = Date.now() - Number(current.lastUpdatedTimestamp);
      if (elapsed < TWENTY_FOUR_HOURS_MS && Array.isArray(current.collections) && current.collections.length > 0) {
        return {
          skipped: true,
          message: 'Las colecciones temáticas ya están actualizadas (<24h).',
          ...current,
        };
      }
    }

    const catalog = this.getCatalog();
    if (!catalog) {
      return {
        skipped: true,
        message: 'No se encontró catalogo.json para muestreo.',
        ...current,
      };
    }

    const { sampleCatalog, catalogMap } = this.extractStratifiedSample(catalog, 75);
    if (sampleCatalog.length === 0) {
      return { skipped: true, message: 'Muestra de catálogo vacía.', ...current };
    }

    this.logger.log('🎨 [Curador IA] Analizando catálogo con el Cerebro de Aura para generar 3 Colecciones Temáticas...');

    const systemPrompt = `Eres el Director Editorial Senior y Máximo Curador Cinematográfico de Audiocinemateca. Tu misión es analizar la muestra del catálogo provisto y diseñar 3 colecciones temáticas extraordinarias, variadas y atractivas para la portada de la aplicación, al nivel de plataformas de streaming premium (Netflix, HBO Max, Criterion Channel).

REGLAS EDITORIALES RIGUROSAS:
1. MÁXIMA DIVERSIDAD Y CONTRASTE TEMÁTICO (OBLIGATORIO):
   - Colección 1 (Pilar Intenso / Adrenalina o Intriga): Thrillers, Giros de Guion, Suspenso, Acción, Crimen o Futuros Distópicos.
   - Colección 2 (Pilar Emocional / Humano o Histórico): Grandes Dramas, Historias de Época, Lazos Familiares, Amores Memorables, Superación o Biografías.
   - Colección 3 (Pilar Imaginación / Culto / Humor o Descubrimiento): Fantasía, Animación, Comedia Inteligente, Joyas de Culto o Documentales Reveladores.
2. TÍTULOS Y SUBTÍTULOS DE NIVEL PREMIUM:
   - PROHIBIDO USAR LA PALABRA "OBRA" O "OBRAS" en ningún título ni descripción.
   - Subtítulo de 1 frase cautivadora que invite inmediatamente a escuchar.
3. CANTIDAD Y EXACTITUD DE ELEMENTOS:
   - Selecciona ENTRE 4 Y 8 TÍTULOS REALES de la muestra proporcionada usando los IDs y títulos EXACTOS.
4. FORMATO ESTRICTO JSON:
{
  "collections": [
    {
      "id": "collection_1",
      "title": "Nombre Creativo de la Colección 1",
      "subtitle": "Frase explicativa atractiva",
      "items": [
        { "id": "ID_EXACTO", "title": "TITULO_EXACTO", "type": "peliculas", "genre": "Genero", "year": "Año" }
      ]
    }
  ]
}`;

    const userPrompt = `Muestra estratificada de ${sampleCatalog.length} títulos del catálogo de Audiocinemateca:\n${JSON.stringify(sampleCatalog)}\n\nGenera las 3 colecciones temáticas en formato JSON estricto.`;

    const responseText = await this.generateWithConfiguredBrain(systemPrompt, userPrompt, 2000);
    if (!responseText) {
      return {
        skipped: true,
        message: 'No se recibió respuesta del proveedor de IA configurado. Verifica la Clave API del Cerebro de Aura.',
        ...current,
      };
    }

    let cleanJsonStr = responseText.trim();
    if (cleanJsonStr.startsWith('```')) {
      cleanJsonStr = cleanJsonStr.replace(/^```(?:json)?\n?/, '').replace(/\n?```$/, '').trim();
    }
    const jsonMatch = cleanJsonStr.match(/\{[\s\S]*\}/);
    const parsedData = JSON.parse(jsonMatch ? jsonMatch[0] : cleanJsonStr);
    if (!parsedData.collections || !Array.isArray(parsedData.collections)) {
      throw new Error('Formato JSON inválido devuelto por la IA.');
    }

    const validatedCollections: any[] = [];
    parsedData.collections.forEach((col: any, idx: number) => {
      const colId = col.id || `collection_${idx + 1}`;
      const colTitle = (col.title || '').trim();
      const colSubtitle = (col.subtitle || col.description || '').trim();
      const rawItems = Array.isArray(col.items) ? col.items : [];

      const validItems: any[] = [];
      const seenInCol = new Set();

      rawItems.forEach((item: any) => {
        const itemIdStr = String(item.id || '').trim();
        if (!itemIdStr || seenInCol.has(itemIdStr)) return;
        const realEntry = catalogMap.get(itemIdStr);
        if (realEntry) {
          seenInCol.add(itemIdStr);
          validItems.push({
            id: realEntry.id,
            title: realEntry.title,
            type: realEntry.type,
            genre: realEntry.genre,
            year: realEntry.year,
          });
        }
      });

      if (colTitle && validItems.length >= 3) {
        validatedCollections.push({
          id: colId,
          title: colTitle,
          subtitle: colSubtitle,
          description: colSubtitle,
          items: validItems,
        });
      }
    });

    if (validatedCollections.length === 0) {
      throw new Error('No se pudieron validar colecciones con suficientes títulos reales.');
    }

    const payload = {
      lastUpdatedTimestamp: Date.now(),
      lastUpdatedIso: new Date().toISOString(),
      updatedAt: Date.now(),
      collections: validatedCollections,
    };

    await this.prisma.appConfig.upsert({
      where: { key: 'curated_collections' },
      update: { value: JSON.stringify(payload) },
      create: {
        key: 'curated_collections',
        value: JSON.stringify(payload),
        description: 'Colecciones temáticas curadas automáticamente por Aura IA (24h)',
      },
    });

    this.logger.log(`✅ [Curador IA] ${validatedCollections.length} Colecciones Temáticas generadas y almacenadas.`);
    return {
      skipped: false,
      message: `${validatedCollections.length} colecciones temáticas generadas con éxito por IA.`,
      ...payload,
    };
  }

  private extractStratifiedSample(catalog: any, totalSampleSize = 75) {
    const rawItems: any[] = [];
    const categories = ['peliculas', 'series', 'documentales', 'cortometrajes'];
    const catalogMap = new Map<string, any>();

    for (const catKey of categories) {
      const items = catalog[catKey] || [];
      items.forEach((item: any) => {
        const title = item.titulo || item.Titulo || item.title;
        const id = item.id;
        const genre = item.genero || 'General';
        const year = item.anio || item.año || 'N/A';
        const director = item.director || '';
        const synopsis = (item.sinopsis || '').substring(0, 160);
        if (title && id) {
          const entry = { id: String(id), title, type: catKey, genre, year, director, synopsis };
          rawItems.push(entry);
          catalogMap.set(String(id), entry);
        }
      });
    }

    const shuffle = <T>(arr: T[]): T[] => {
      const copy = [...arr];
      for (let i = copy.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [copy[i], copy[j]] = [copy[j], copy[i]];
      }
      return copy;
    };

    if (rawItems.length === 0) return { sampleCatalog: [], catalogMap };

    const buckets: Record<string, any[]> = {
      adrenalina: [],
      scifi_terror: [],
      drama_romance: [],
      comedia_familiar: [],
      culto_documental: [],
    };

    rawItems.forEach((item) => {
      const text = `${item.genre} ${item.synopsis} ${item.type}`.toLowerCase();
      if (text.includes('documental') || text.includes('culto') || text.includes('naturaleza') || text.includes('biograf')) {
        buckets.culto_documental.push(item);
      } else if (text.includes('comedia') || text.includes('animac') || text.includes('anime') || text.includes('infantil')) {
        buckets.comedia_familiar.push(item);
      } else if (text.includes('drama') || text.includes('romance') || text.includes('históric') || text.includes('época')) {
        buckets.drama_romance.push(item);
      } else if (text.includes('ciencia ficción') || text.includes('terror') || text.includes('fantasía') || text.includes('misterio')) {
        buckets.scifi_terror.push(item);
      } else {
        buckets.adrenalina.push(item);
      }
    });

    const targetPerBucket = Math.ceil(totalSampleSize / 5);
    const sample: any[] = [];
    const usedIds = new Set<string>();

    Object.values(buckets).forEach((bList) => {
      shuffle(bList)
        .slice(0, targetPerBucket)
        .forEach((item) => {
          if (!usedIds.has(item.id)) {
            usedIds.add(item.id);
            sample.push({
              id: item.id,
              title: item.title,
              type: item.type,
              genre: item.genre,
              year: item.year,
              director: item.director || undefined,
              synopsis: (item.synopsis || '').substring(0, 90),
            });
          }
        });
    });

    return { sampleCatalog: shuffle(sample), catalogMap };
  }

  // ===========================================================================
  // DESPACHADOR UNIFICADO DE IA: GROQ -> GEMINI -> PERSONALIZADO
  // ===========================================================================

  async generateWithConfiguredBrain(
    systemPrompt: string,
    userPrompt: string,
    overrideMaxTokens?: number,
  ): Promise<string | null> {
    const auraCfg = await this.getAuraConfig();

    // Orden de resolución: primero el proveedor activo configurado por el administrador;
    // si no tiene clave configurada, intenta fallback con cualquiera de los otros proveedores con clave activa
    const providerOrder: AiProviderType[] = [
      auraCfg.provider,
      ...(['groq', 'gemini', 'custom'] as AiProviderType[]).filter((p) => p !== auraCfg.provider),
    ];

    for (const provider of providerOrder) {
      const resolvedKey = await this.resolveProviderApiKey(provider);
      if (!resolvedKey.key) continue;

      const isPrimaryProvider = provider === auraCfg.provider;
      const catalogEntry =
        AI_PROVIDER_CATALOG.find((p) => p.id === provider) || AI_PROVIDER_CATALOG[0];
      const modelToUse = isPrimaryProvider ? auraCfg.model : catalogEntry.defaultModel;

      const reply = await this.dispatchBrainCompletion({
        provider,
        apiKey: resolvedKey.key,
        model: modelToUse,
        systemPrompt,
        userPrompt,
        thinkingEnabled: auraCfg.thinkingEnabled,
        reasoningEffort: auraCfg.reasoningEffort || 'medium',
        temperature: auraCfg.temperature ?? 0.7,
        maxTokens: overrideMaxTokens || auraCfg.maxTokens || 700,
        customBaseUrl: auraCfg.customBaseUrl,
      });

      if (reply) return reply;
    }

    this.logger.warn('⚠️ [Aura Brain] Ningún proveedor de IA configurado tiene clave activa o respondió.');
    return null;
  }

  private async dispatchBrainCompletion(params: {
    provider: AiProviderType;
    apiKey: string;
    model: string;
    systemPrompt: string;
    userPrompt: string;
    thinkingEnabled: boolean;
    reasoningEffort: ReasoningEffortType;
    temperature: number;
    maxTokens: number;
    customBaseUrl?: string;
  }): Promise<string | null> {
    if (params.provider === 'groq') {
      return this.executeOpenAiCompatibleRequest({
        baseUrl: 'https://api.groq.com/openai/v1',
        apiKey: params.apiKey,
        model: params.model || AI_PROVIDER_CATALOG[0].defaultModel,
        systemPrompt: params.systemPrompt,
        userPrompt: params.userPrompt,
        thinkingEnabled: params.thinkingEnabled,
        reasoningEffort: params.reasoningEffort,
        temperature: params.temperature,
        maxTokens: params.maxTokens,
        isGroq: true,
      });
    }

    if (params.provider === 'custom') {
      const baseUrl = (params.customBaseUrl || 'https://api.openai.com/v1').replace(/\/+$/, '');
      return this.executeOpenAiCompatibleRequest({
        baseUrl,
        apiKey: params.apiKey,
        model: params.model || AI_PROVIDER_CATALOG[2].defaultModel,
        systemPrompt: params.systemPrompt,
        userPrompt: params.userPrompt,
        thinkingEnabled: params.thinkingEnabled,
        reasoningEffort: params.reasoningEffort,
        temperature: params.temperature,
        maxTokens: params.maxTokens,
        isGroq: baseUrl.includes('groq.com'),
      });
    }

    // Proveedor 2: Google Gemini
    return this.executeGeminiRequest(
      params.apiKey,
      params.model || AI_PROVIDER_CATALOG[1].defaultModel,
      params.systemPrompt,
      params.userPrompt,
      params.thinkingEnabled,
      params.reasoningEffort,
      params.temperature,
      params.maxTokens,
    );
  }

  /**
   * Cliente HTTP oficial para Groq (`https://api.groq.com/openai/v1/chat/completions`)
   * y cualquier proveedor OpenAI-Compatible personalizado
   */
  private executeOpenAiCompatibleRequest(
    params: {
      baseUrl: string;
      apiKey: string;
      model: string;
      systemPrompt: string;
      userPrompt: string;
      thinkingEnabled: boolean;
      reasoningEffort: ReasoningEffortType;
      temperature: number;
      maxTokens: number;
      isGroq: boolean;
    },
    stripReasoningParams = false,
  ): Promise<string | null> {
    return new Promise((resolve) => {
      const lowerModel = params.model.toLowerCase();
      const isGptOss = lowerModel.includes('gpt-oss');
      const isQwen3 = lowerModel.includes('qwen3') || lowerModel.includes('qwen-3');
      const isDeepSeekR1 = lowerModel.includes('deepseek-r1') || lowerModel.includes('reasoner');
      const isOSeries = /^o[134]/.test(lowerModel) || lowerModel.includes('/o1') || lowerModel.includes('/o3');

      const bodyObj: any = {
        model: params.model,
        messages: [
          { role: 'system', content: params.systemPrompt },
          { role: 'user', content: params.userPrompt },
        ],
        temperature: params.temperature,
        max_completion_tokens: params.maxTokens,
      };

      if (!stripReasoningParams) {
        if (params.isGroq) {
          if (isGptOss) {
            bodyObj.reasoning_effort = params.thinkingEnabled ? params.reasoningEffort : 'low';
            bodyObj.include_reasoning = false;
          } else if (isQwen3) {
            bodyObj.reasoning_effort = params.thinkingEnabled ? 'default' : 'none';
            bodyObj.reasoning_format = 'hidden';
          } else if (isDeepSeekR1) {
            bodyObj.reasoning_format = 'hidden';
          }
        } else if (isOSeries || (params.thinkingEnabled && isDeepSeekR1)) {
          if (isOSeries) {
            bodyObj.reasoning_effort = params.thinkingEnabled ? params.reasoningEffort : 'low';
          }
        }
      }

      const payload = JSON.stringify(bodyObj);
      const endpoint = params.baseUrl.endsWith('/chat/completions')
        ? params.baseUrl
        : `${params.baseUrl.replace(/\/+$/, '')}/chat/completions`;

      let uri: URL;
      try {
        uri = new URL(endpoint);
      } catch {
        resolve(null);
        return;
      }

      const client = uri.protocol === 'http:' ? http : https;
      const req = client.request(
        uri,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${params.apiKey}`,
            'Content-Length': Buffer.byteLength(payload),
          },
        },
        (res) => {
          let body = '';
          res.on('data', (chunk) => (body += chunk));
          res.on('end', () => {
            try {
              if (res.statusCode && res.statusCode >= 200 && res.statusCode < 300) {
                const parsed = JSON.parse(body);
                const rawContent = parsed?.choices?.[0]?.message?.content || '';
                const cleanText = String(rawContent)
                  .replace(/<think>[\s\S]*?<\/think>/gi, '')
                  .replace(/<thought>[\s\S]*?<\/thought>/gi, '')
                  .replace(/<reasoning>[\s\S]*?<\/reasoning>/gi, '')
                  .trim();
                resolve(cleanText || null);
              } else if (res.statusCode === 400 && !stripReasoningParams) {
                // Reintento limpio automático si algún modelo no soporta parámetros de reasoning
                this.executeOpenAiCompatibleRequest(params, true).then(resolve);
              } else {
                this.logger.warn(
                  `⚠️ [Aura Brain • ${params.isGroq ? 'Groq' : 'Custom'}] HTTP ${res.statusCode} (${params.model}): ${body.substring(0, 220)}`,
                );
                resolve(null);
              }
            } catch {
              resolve(null);
            }
          });
        },
      );

      req.setTimeout(params.thinkingEnabled ? 45000 : 20000, () => {
        req.destroy(new Error('Timeout AI Provider'));
      });

      req.on('error', (err) => {
        this.logger.warn(`⚠️ [Aura Brain] Error de red (${endpoint}): ${err.message}`);
        resolve(null);
      });

      req.write(payload);
      req.end();
    });
  }

  /**
   * Cliente HTTP para Google Gemini / Gemma 4 con soporte de Pensamiento Profundo
   */
  private executeGeminiRequest(
    apiKey: string,
    modelName: string,
    systemPrompt: string,
    userPrompt: string,
    enableDeepThinking = false,
    reasoningEffort: ReasoningEffortType = 'medium',
    temperature = 0.7,
    maxTokens = 700,
    retries = 1,
  ): Promise<string | null> {
    return new Promise((resolve) => {
      const attemptRequest = (currentRetry: number) => {
        const lowerModel = modelName.toLowerCase();
        const isGemma = lowerModel.includes('gemma');
        const isGemma3OrOlder = lowerModel.includes('gemma-2') || lowerModel.includes('gemma-3');

        const payloadObj: any = {
          contents: [
            {
              role: 'user',
              parts: [{ text: userPrompt }],
            },
          ],
          generationConfig: {
            temperature,
            maxOutputTokens: maxTokens,
          },
        };

        if (lowerModel.includes('gemma-4') || lowerModel.includes('gemini-3')) {
          payloadObj.generationConfig.thinkingConfig = {
            thinkingLevel: enableDeepThinking ? reasoningEffort : 'minimal',
          };
        } else if (!isGemma && lowerModel.includes('gemini-2.5')) {
          if (enableDeepThinking) {
            const budget =
              reasoningEffort === 'high' ? 4096 : reasoningEffort === 'low' ? 1024 : 2048;
            payloadObj.generationConfig.thinkingConfig = {
              thinkingBudget: budget,
            };
          } else {
            payloadObj.generationConfig.thinkingConfig = {
              thinkingBudget: lowerModel.includes('pro') ? 128 : 0,
            };
          }
        }

        if (!isGemma3OrOlder) {
          payloadObj.systemInstruction = { parts: [{ text: systemPrompt }] };
        } else {
          payloadObj.contents[0].parts[0].text = `${systemPrompt}\n\n${userPrompt}`;
        }

        const payload = JSON.stringify(payloadObj);
        const url = `https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent?key=${apiKey}`;
        const uri = new URL(url);

        const req = https.request(
          uri,
          {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              'Content-Length': Buffer.byteLength(payload),
            },
          },
          (res) => {
            let body = '';
            res.on('data', (chunk) => (body += chunk));
            res.on('end', () => {
              try {
                if (res.statusCode && res.statusCode >= 200 && res.statusCode < 300) {
                  const parsed = JSON.parse(body);
                  const parts = parsed?.candidates?.[0]?.content?.parts || [];
                  let rawText = '';
                  for (const p of parts) {
                    if (p.thought === true || p.thinking === true) continue;
                    if (p.text) rawText += p.text;
                  }
                  const cleanText = rawText
                    .replace(/<think>[\s\S]*?<\/think>/gi, '')
                    .replace(/<thought>[\s\S]*?<\/thought>/gi, '')
                    .replace(/<reasoning>[\s\S]*?<\/reasoning>/gi, '')
                    .trim();

                  resolve(cleanText || null);
                } else if ((res.statusCode === 503 || res.statusCode === 429) && currentRetry > 0) {
                  setTimeout(() => attemptRequest(currentRetry - 1), 2000);
                } else {
                  this.logger.warn(
                    `⚠️ [Aura Brain • Gemini] HTTP ${res.statusCode} (${modelName}): ${body.substring(0, 220)}`,
                  );
                  resolve(null);
                }
              } catch {
                resolve(null);
              }
            });
          },
        );

        const timeoutMs = enableDeepThinking ? 60000 : 18000;
        req.setTimeout(timeoutMs, () => {
          req.destroy(new Error('Timeout Gemini'));
        });

        req.on('error', () => {
          if (currentRetry > 0) {
            setTimeout(() => attemptRequest(currentRetry - 1), 2000);
          } else {
            resolve(null);
          }
        });

        req.write(payload);
        req.end();
      };

      attemptRequest(retries);
    });
  }

  private httpGetJson(url: string, headers: Record<string, string> = {}): Promise<any> {
    return new Promise((resolve, reject) => {
      const uri = new URL(url);
      const client = uri.protocol === 'http:' ? http : https;
      const req = client.request(
        uri,
        {
          method: 'GET',
          headers: {
            Accept: 'application/json',
            ...headers,
          },
        },
        (res) => {
          let body = '';
          res.on('data', (c) => (body += c));
          res.on('end', () => {
            if (res.statusCode && res.statusCode >= 200 && res.statusCode < 300) {
              try {
                resolve(JSON.parse(body));
              } catch (e) {
                reject(e);
              }
            } else {
              reject(new Error(`HTTP ${res.statusCode}`));
            }
          });
        },
      );
      req.setTimeout(10000, () => req.destroy(new Error('Timeout GET models')));
      req.on('error', reject);
      req.end();
    });
  }
}
