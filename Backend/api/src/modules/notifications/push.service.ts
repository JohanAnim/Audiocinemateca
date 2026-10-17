import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JWT } from 'google-auth-library';
import { PrismaService } from '../../prisma/prisma.service.js';
import * as https from 'https';

export interface GlobalPushInput {
  title: string;
  body: string;
  destination?: 'app' | 'external_url' | 'content_detail' | 'donation' | 'catalog' | 'announcements' | 'global_chat';
  url?: string;
  contentType?: string;
  contentId?: string;
  delayMinutes?: number;
  target?: 'all' | 'android' | 'desktop';
  sentBy?: string;
}

export type GlobalPushPayload = GlobalPushInput;

export interface PushHistoryEntry {
  id: string;
  title: string;
  body: string;
  destination: string;
  target: string;
  status: 'SENT' | 'SCHEDULED' | 'FAILED';
  fcmResponse?: string;
  error?: string;
  scheduledFor?: string;
  createdAt: string;
}

@Injectable()
export class PushService {
  private readonly logger = new Logger(PushService.name);
  private jwtClient: JWT | null = null;
  private projectId: string | null = null;

  constructor(
    private readonly configService: ConfigService,
    private readonly prisma: PrismaService,
  ) {
    this.initFcmClient();
  }

  private initFcmClient() {
    const projectId = this.configService.get<string>('FIREBASE_PROJECT_ID');
    const clientEmail = this.configService.get<string>('FIREBASE_CLIENT_EMAIL');
    const rawPrivateKey = this.configService.get<string>('FIREBASE_PRIVATE_KEY');

    if (projectId && clientEmail && rawPrivateKey) {
      try {
        const privateKey = rawPrivateKey.replace(/\\n/g, '\n').replace(/^["']|["']$/g, '');
        this.projectId = projectId.replace(/^["']|["']$/g, '');
        this.jwtClient = new JWT({
          email: clientEmail.replace(/^["']|["']$/g, ''),
          key: privateKey,
          scopes: ['https://www.googleapis.com/auth/firebase.messaging'],
        });
        this.logger.log(`🔔 Servicio FCM HTTP v1 inicializado para proyecto "${this.projectId}"`);
      } catch (err: any) {
        this.logger.warn(`⚠️ Error inicializando cliente FCM: ${err.message}`);
      }
    } else {
      this.logger.warn('⚠️ Credenciales FCM no configuradas en .env (modo registro local)');
    }
  }

  private async getAccessToken(): Promise<string | null> {
    if (!this.jwtClient) return null;
    try {
      const tokens = await this.jwtClient.authorize();
      return tokens.access_token || null;
    } catch (err: any) {
      this.logger.error(`❌ Error obteniendo Access Token de FCM: ${err.message}`);
      return null;
    }
  }

  async sendRawFcmMessage(messagePayload: {
    topic?: string;
    token?: string;
    data: Record<string, string>;
  }): Promise<{ success: boolean; messageId?: string; error?: string }> {
    const accessToken = await this.getAccessToken();
    if (!accessToken || !this.projectId) {
      return {
        success: false,
        error: 'Credenciales FCM no disponibles o inválidas',
      };
    }

    const stringifiedData: Record<string, string> = {};
    for (const [k, v] of Object.entries(messagePayload.data || {})) {
      if (v !== undefined && v !== null) {
        stringifiedData[k] = String(v);
      }
    }

    const fcmBody: any = {
      message: {
        data: stringifiedData,
        android: {
          priority: 'HIGH',
          ttl: '86400s',
        },
      },
    };

    if (messagePayload.topic) {
      fcmBody.message.topic = messagePayload.topic;
    } else if (messagePayload.token) {
      fcmBody.message.token = messagePayload.token;
    } else {
      fcmBody.message.topic = 'audiocinemateca_global';
    }

    const payloadStr = JSON.stringify(fcmBody);

    return new Promise((resolve) => {
      const req = https.request(
        {
          hostname: 'fcm.googleapis.com',
          path: `/v1/projects/${this.projectId}/messages:send`,
          method: 'POST',
          headers: {
            Authorization: `Bearer ${accessToken}`,
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(payloadStr),
          },
        },
        (res) => {
          let body = '';
          res.on('data', (chunk) => (body += chunk));
          res.on('end', () => {
            try {
              const parsed = JSON.parse(body);
              if (res.statusCode && res.statusCode >= 200 && res.statusCode < 300) {
                resolve({ success: true, messageId: parsed.name });
              } else {
                const errMsg = parsed?.error?.message || `HTTP ${res.statusCode}`;
                resolve({ success: false, error: errMsg });
              }
            } catch {
              resolve({ success: false, error: `Respuesta inválida HTTP ${res.statusCode}` });
            }
          });
        },
      );

      req.on('error', (err) => {
        resolve({ success: false, error: err.message });
      });

      req.write(payloadStr);
      req.end();
    });
  }

  // --- 1. ENVIAR NOTIFICACIÓN GLOBAL (CON SOPORTE DE PROGRAMACIÓN Y DESTINOS) ---
  async sendGlobalPush(input: GlobalPushInput) {
    const title = (input.title || 'Audiocinemateca').trim();
    const body = (input.body || '').trim();
    const destination = input.destination || 'app';
    const target = input.target || 'all';
    const delayMinutes = Number(input.delayMinutes || 0);

    const dataPayload: Record<string, string> = {
      destination,
      title,
      body: body.replace(/\n/g, ' ').substring(0, 1000),
      fullText: body,
      remoteId: `G_${Date.now()}`,
    };

    if (destination === 'external_url' && input.url) {
      dataPayload.url = input.url.trim();
    } else if (destination === 'content_detail' && input.contentId) {
      const selectedType = input.contentType || 'peliculas';
      dataPayload.contentType = selectedType;
      dataPayload.type = selectedType;
      dataPayload.contentId = input.contentId.trim();
      dataPayload.url =
        input.url?.trim() || `https://audiocinemateca.com/${selectedType}?id=${input.contentId.trim()}`;
    } else if (destination === 'donation') {
      dataPayload.url =
        input.url?.trim() || 'https://www.paypal.com/donate/?hosted_button_id=T4H2LCSZDRV8J';
    }

    const executeSend = async () => {
      const res = await this.sendRawFcmMessage({
        topic: 'audiocinemateca_global',
        data: dataPayload,
      });

      await this.recordPushHistory({
        id: dataPayload.remoteId,
        title,
        body,
        destination,
        target,
        status: res.success ? 'SENT' : 'FAILED',
        fcmResponse: res.messageId,
        error: res.error,
        createdAt: new Date().toISOString(),
      });

      if (res.success) {
        this.logger.log(`✅ Push global enviado con éxito: "${title}" (${res.messageId})`);
      } else {
        this.logger.warn(`⚠️ Falló envío FCM global: ${res.error}`);
      }
      return res;
    };

    if (delayMinutes > 0) {
      const scheduledTime = new Date(Date.now() + delayMinutes * 60 * 1000).toISOString();
      setTimeout(() => {
        executeSend().catch((e) => this.logger.error(e));
      }, delayMinutes * 60 * 1000);

      const entry: PushHistoryEntry = {
        id: dataPayload.remoteId,
        title,
        body,
        destination,
        target,
        status: 'SCHEDULED',
        scheduledFor: scheduledTime,
        createdAt: new Date().toISOString(),
      };
      await this.recordPushHistory(entry);

      return {
        success: true,
        scheduled: true,
        scheduledFor: scheduledTime,
        message: `Notificación programada para enviarse en ${delayMinutes} minuto(s).`,
      };
    }

    const result = await executeSend();
    return {
      success: result.success,
      scheduled: false,
      messageId: result.messageId,
      error: result.error,
      message: result.success
        ? 'Notificación Push enviada exitosamente a toda la comunidad.'
        : `Registrada en el panel (Aviso FCM: ${result.error})`,
    };
  }

  // --- 2. NOTIFICACIÓN DE ANUNCIO OFICIAL ---
  async sendAnnouncementPush(announcementId: string, title: string, content: string) {
    return this.sendRawFcmMessage({
      topic: 'audiocinemateca_global',
      data: {
        title: title || 'Nuevo Anuncio Oficial',
        body: content.replace(/\n/g, ' ').substring(0, 1000),
        fullText: content,
        destination: 'announcements',
        remoteId: announcementId,
      },
    });
  }

  // --- 3. NOTIFICACIONES DE MENCIONES Y RESPUESTAS EN EL CHAT GLOBAL ---
  async notifyChatMentionsAndReplies(params: {
    messageId: string;
    senderId: string;
    senderName: string;
    senderRole: string;
    messageText: string;
  }) {
    const { messageId, senderId, senderName, senderRole, messageText } = params;
    if (!messageText) return;

    const lowerText = messageText.toLowerCase();

    // 1. Mención masiva @todos por un Administrador
    if (lowerText.includes('@todos') && (senderRole === 'ADMIN' || senderRole === 'MODERATOR')) {
      await this.sendRawFcmMessage({
        topic: 'audiocinemateca_global',
        data: {
          destination: 'global_chat',
          targetKey: 'todos',
          title: `Nueva notificación en el Chat Global: ${senderName} anunció a Todos`,
          body: messageText,
          senderId,
          remoteId: `chat_${messageId}`,
        },
      });
      this.logger.log(`📣 Notificación @todos enviada al chat global por ${senderName}`);
      return;
    }

    // 2. Menciones individuales (@usuario)
    const mentionMatches = messageText.match(/@([a-zA-Z0-9_\-.áéíóúÁÉÍÓÚñÑ]+)/g);
    if (!mentionMatches || mentionMatches.length === 0) return;

    const mentionedHandles = Array.from(
      new Set(
        mentionMatches
          .map((m) => m.replace('@', '').trim().toLowerCase())
          .filter((m) => m && m !== 'aura' && m !== 'todos'),
      ),
    );

    if (mentionedHandles.length === 0) return;

    for (const handle of mentionedHandles) {
      const targetUser = await this.prisma.user.findFirst({
        where: {
          OR: [
            { username: { equals: handle, mode: 'insensitive' } },
            { displayName: { equals: handle, mode: 'insensitive' } },
          ],
          NOT: { id: senderId },
        },
        include: {
          devices: {
            where: { fcmToken: { not: '' } },
            orderBy: { lastActive: 'desc' },
            take: 3,
          },
        },
      });

      if (targetUser && targetUser.devices.length > 0) {
        for (const dev of targetUser.devices) {
          if (dev.fcmToken) {
            await this.sendRawFcmMessage({
              token: dev.fcmToken,
              data: {
                destination: 'global_chat',
                targetUid: targetUser.id,
                targetKey: handle,
                title: `Nueva notificación en el Chat Global: ${senderName} te mencionó`,
                body: messageText,
                senderId,
                remoteId: `chat_${messageId}`,
              },
            });
          }
        }
      }
    }
  }

  // --- 4. NOTIFICACIÓN DE NUEVOS COMENTARIOS EN OBRAS ---
  async notifyPreviousCommenters(params: {
    commentId: string;
    authorId: string;
    authorName: string;
    contentId: string;
    contentTitle: string;
    contentType: string;
    commentText: string;
    partIndex?: number | null;
    episodeIndex?: number | null;
  }) {
    try {
      const previousComments = await this.prisma.comment.findMany({
        where: {
          contentId: params.contentId,
          userId: { not: params.authorId },
        },
        select: { userId: true },
        distinct: ['userId'],
      });

      if (previousComments.length === 0) return;

      const targetUserIds = previousComments.map((c) => c.userId);
      const devices = await this.prisma.userDevice.findMany({
        where: {
          userId: { in: targetUserIds },
          fcmToken: { not: '' },
        },
      });

      for (const dev of devices) {
        if (dev.fcmToken) {
          await this.sendRawFcmMessage({
            token: dev.fcmToken,
            data: {
              destination: 'comments',
              contentId: params.contentId,
              contentType: params.contentType.toLowerCase(),
              partIndex: String(params.partIndex ?? 0),
              episodeIndex: String(params.episodeIndex ?? -1),
              title: `${params.authorName} también comentó en ${params.contentTitle}`,
              body: params.commentText,
              remoteId: `comment_${params.commentId}`,
            },
          });
        }
      }
    } catch (err: any) {
      this.logger.warn(`⚠️ Error notificando comentarios previos: ${err.message}`);
    }
  }

  // --- HISTORIAL DE NOTIFICACIONES PUSH ---
  async getPushHistory(): Promise<PushHistoryEntry[]> {
    const cfg = await this.prisma.appConfig.findUnique({
      where: { key: 'push_notifications_history' },
    });
    if (!cfg) return [];
    try {
      const parsed = JSON.parse(cfg.value);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  private async recordPushHistory(entry: PushHistoryEntry) {
    try {
      const current = await this.getPushHistory();
      const updated = [entry, ...current.filter((item) => item.id !== entry.id)].slice(0, 30);
      await this.prisma.appConfig.upsert({
        where: { key: 'push_notifications_history' },
        update: { value: JSON.stringify(updated) },
        create: {
          key: 'push_notifications_history',
          value: JSON.stringify(updated),
          description: 'Historial reciente de notificaciones Push enviadas desde el panel',
        },
      });
    } catch (err: any) {
      this.logger.warn(`Error guardando historial push: ${err.message}`);
    }
  }
}
