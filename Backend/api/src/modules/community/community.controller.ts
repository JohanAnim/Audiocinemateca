import {
  Controller,
  Get,
  Post,
  Delete,
  Body,
  Param,
  Query,
  UseGuards,
  HttpCode,
  HttpStatus,
  ForbiddenException,
} from '@nestjs/common';
import { CommunityService } from './community.service.js';
import { ChatGateway } from './gateways/chat.gateway.js';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard.js';
import { Public } from '../auth/decorators/public.decorator.js';
import { CurrentUser } from '../auth/decorators/current-user.decorator.js';
import { Role } from '@prisma/client';
import type { User } from '@prisma/client';

@Controller('api/v1/community')
@UseGuards(JwtAuthGuard)
export class CommunityController {
  constructor(
    private readonly communityService: CommunityService,
    private readonly chatGateway: ChatGateway,
  ) {}

  // --- 1. HISTORIAL DE MENSAJES DE CHAT ---
  @Get('chat/messages')
  async getChatMessages(
    @Query('page') page?: number,
    @Query('limit') limit?: number,
  ) {
    const p = page ? Number(page) : 1;
    const l = limit ? Math.min(Number(limit), 100) : 50;
    return this.communityService.getMessages(p, l);
  }

  @Delete('chat/messages/:id')
  async deleteMessage(
    @CurrentUser() user: User,
    @Param('id') messageId: string,
  ) {
    const result = await this.communityService.deleteMessage(user.id, messageId, user.role);
    // Notificar también por WebSocket
    this.chatGateway.server?.emit('message_deleted', { messageId });
    return result;
  }

  // --- 2. USUARIOS EN LÍNEA ---
  @Public()
  @Get('online')
  async getOnlineUsers() {
    const users = this.chatGateway.getOnlineUsersList();
    return {
      count: users.length,
      users: users.map((u) => ({
        id: u.id,
        userId: u.id,
        username: u.username,
        displayName: u.displayName,
        avatarUrl: u.avatarUrl,
        role: u.role,
        deviceInfo: u.deviceInfo || 'Android',
        currentlyListening: u.currentlyListening || null,
        online: true,
      })),
    };
  }

  // --- 3. ANUNCIOS OFICIALES ---
  @Public()
  @Get('announcements')
  async getAnnouncements(@Query('limit') limit?: number) {
    const l = limit ? Math.min(Number(limit), 50) : 20;
    return this.communityService.getAnnouncements(l);
  }

  @Post('announcements')
  @HttpCode(HttpStatus.CREATED)
  async createAnnouncement(
    @CurrentUser() user: User,
    @Body() body: { content: string; title?: string },
  ) {
    if (user.role !== Role.ADMIN) {
      throw new ForbiddenException('Solo administradores pueden publicar anuncios oficiales');
    }

    const announcement = await this.communityService.createAnnouncement(
      user.id,
      body.content,
      body.title,
    );

    // Notificar por WebSocket a todos los usuarios conectados
    this.chatGateway.server?.emit('new_announcement', announcement);

    return announcement;
  }

  @Delete('announcements/:id')
  async deleteAnnouncement(
    @CurrentUser('role') role: Role,
    @Param('id') announcementId: string,
  ) {
    if (role !== Role.ADMIN) {
      throw new ForbiddenException('Solo administradores pueden eliminar anuncios');
    }

    return this.communityService.deleteAnnouncement(announcementId);
  }

  // --- 4. MODERACIÓN Y BANEO ---
  @Post('ban')
  @HttpCode(HttpStatus.OK)
  async banUser(
    @CurrentUser() user: User,
    @Body() body: { targetUserId: string; reason: string; durationHours?: number },
  ) {
    if (user.role !== Role.ADMIN && user.role !== Role.MODERATOR) {
      throw new ForbiddenException('No tienes permisos de moderación');
    }

    const banRecord = await this.communityService.banUser(
      user.id,
      body.targetUserId,
      body.reason,
      body.durationHours,
    );

    // Emitir evento por socket si el usuario está conectado
    this.chatGateway.server?.emit('user_banned', {
      userId: body.targetUserId,
      reason: body.reason,
    });

    return banRecord;
  }

  @Post('unban')
  @HttpCode(HttpStatus.OK)
  async unbanUser(
    @CurrentUser('role') role: Role,
    @Body() body: { targetUserId: string },
  ) {
    if (role !== Role.ADMIN && role !== Role.MODERATOR) {
      throw new ForbiddenException('No tienes permisos de moderación');
    }

    return this.communityService.unbanUser(body.targetUserId);
  }
}
