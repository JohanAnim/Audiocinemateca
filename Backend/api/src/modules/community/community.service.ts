import {
  Injectable,
  NotFoundException,
  ForbiddenException,
  BadRequestException,
} from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service.js';
import { PushService } from '../notifications/push.service.js';
import { AppConfigService } from '../app-config/app-config.service.js';
import { Role } from '@prisma/client';

@Injectable()
export class CommunityService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly pushService: PushService,
    private readonly appConfigService: AppConfigService,
  ) {}

  // --- 1. CHAT GLOBAL Y MENSAJES ---
  async getMessages(page = 1, limit = 50) {
    const skip = (page - 1) * limit;

    const [total, messages] = await Promise.all([
      this.prisma.chatMessage.count(),
      this.prisma.chatMessage.findMany({
        skip,
        take: limit,
        orderBy: { createdAt: 'desc' },
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
      }),
    ]);

    // Devolver en orden cronológico ascendente para la UI de chat
    const chronological = messages.reverse();

    return {
      data: chronological,
      meta: {
        total,
        page,
        limit,
        totalPages: Math.ceil(total / limit),
      },
    };
  }

  async saveMessage(userId: string, content: string, userRole?: Role) {
    const isBanned = await this.isUserBanned(userId);
    if (isBanned.banned) {
      throw new ForbiddenException(`Estás temporalmente suspendido del chat: ${isBanned.reason}`);
    }

    const chatConfig = await this.appConfigService.getParsedConfig<any>('chat_config');
    if (chatConfig && chatConfig.enabled === false && userRole !== Role.ADMIN && userRole !== Role.MODERATOR) {
      const closedMsg =
        chatConfig.closedMessage ||
        'El chat de la comunidad se encuentra temporalmente cerrado por mantenimiento o moderación.';
      throw new ForbiddenException(closedMsg);
    }

    return this.prisma.chatMessage.create({
      data: {
        userId,
        content: content.trim(),
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
  }

  async deleteMessage(userId: string, messageId: string, role: Role) {
    const message = await this.prisma.chatMessage.findUnique({
      where: { id: messageId },
    });

    if (!message) {
      throw new NotFoundException('Mensaje no encontrado');
    }

    if (message.userId !== userId && role !== Role.ADMIN && role !== Role.MODERATOR) {
      throw new ForbiddenException('No tienes permisos para eliminar este mensaje');
    }

    await this.prisma.chatMessage.delete({
      where: { id: messageId },
    });

    return { success: true, messageId };
  }

  // --- 2. ANUNCIOS OFICIALES ---
  async getAnnouncements(limit = 20) {
    return this.prisma.announcement.findMany({
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        author: {
          select: {
            id: true,
            displayName: true,
            username: true,
            role: true,
          },
        },
      },
    });
  }

  async createAnnouncement(authorId: string, content: string, title?: string) {
    const announcement = await this.prisma.announcement.create({
      data: {
        authorId,
        content: content.trim(),
        title: title?.trim(),
      },
      include: {
        author: {
          select: {
            id: true,
            displayName: true,
            username: true,
            role: true,
          },
        },
      },
    });

    // Disparar notificación Push FCM a todos los dispositivos
    this.pushService
      .sendAnnouncementPush(
        announcement.id,
        title?.trim() || 'Anuncio Oficial de la Comunidad',
        content.trim(),
      )
      .catch(() => {});

    return announcement;
  }

  async deleteAnnouncement(announcementId: string) {
    await this.prisma.announcement.delete({
      where: { id: announcementId },
    });
    return { success: true };
  }

  // --- 3. MODERACIÓN Y BANEO ---
  async isUserBanned(userId: string): Promise<{ banned: boolean; reason?: string }> {
    const record = await this.prisma.bannedUser.findUnique({
      where: { userId },
    });

    if (!record) {
      return { banned: false };
    }

    // Verificar si el baneo ya expiró
    if (record.bannedUntil && record.bannedUntil.getTime() <= Date.now()) {
      await this.prisma.bannedUser.delete({ where: { userId } });
      return { banned: false };
    }

    return { banned: true, reason: record.reason };
  }

  async banUser(bannedById: string, targetUserId: string, reason: string, durationHours?: number) {
    if (bannedById === targetUserId) {
      throw new BadRequestException('No puedes auto-suspenderte.');
    }

    const targetUser = await this.prisma.user.findUnique({
      where: { id: targetUserId },
    });

    if (!targetUser) {
      throw new NotFoundException('Usuario a sancionar no encontrado.');
    }

    if (targetUser.role === Role.ADMIN) {
      throw new ForbiddenException('No puedes suspender a un administrador.');
    }

    const bannedUntil = durationHours
      ? new Date(Date.now() + durationHours * 3600 * 1000)
      : null;

    return this.prisma.bannedUser.upsert({
      where: { userId: targetUserId },
      update: {
        reason: reason.trim(),
        bannedById,
        bannedUntil,
      },
      create: {
        userId: targetUserId,
        reason: reason.trim(),
        bannedById,
        bannedUntil,
      },
      include: {
        user: {
          select: { id: true, username: true, displayName: true, email: true },
        },
      },
    });
  }

  async unbanUser(targetUserId: string) {
    await this.prisma.bannedUser.deleteMany({
      where: { userId: targetUserId },
    });
    return { success: true };
  }
}
