import {
  Controller,
  Get,
  Post,
  Put,
  Body,
  Query,
  UseGuards,
  ForbiddenException,
} from '@nestjs/common';
import { AdminService } from './admin.service.js';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard.js';
import { CurrentUser } from '../auth/decorators/current-user.decorator.js';
import { Role } from '@prisma/client';
import type { User } from '@prisma/client';
import type { GlobalPushPayload } from '../notifications/push.service.js';

@Controller('api/v1/admin')
@UseGuards(JwtAuthGuard)
export class AdminController {
  constructor(private readonly adminService: AdminService) {}

  private ensureModeratorOrAdmin(user: User) {
    if (user.role !== Role.ADMIN && user.role !== Role.MODERATOR) {
      throw new ForbiddenException('Acceso restringido a administradores y moderadores');
    }
  }

  private ensureAdmin(user: User) {
    if (user.role !== Role.ADMIN) {
      throw new ForbiddenException('Acceso restringido exclusivamente a administradores');
    }
  }

  @Get('stats')
  async getStats(@CurrentUser() user: User) {
    this.ensureModeratorOrAdmin(user);
    return this.adminService.getExecutiveStats();
  }

  // --- CEREBRO MULTI-PROVEEDOR DE AURA 2.0 (1. GROQ, 2. GEMINI, 3. PERSONALIZADO) ---
  @Get('ai/brain')
  async getAuraBrain(@CurrentUser() user: User) {
    this.ensureModeratorOrAdmin(user);
    return this.adminService.getAuraBrainConfig();
  }

  @Put('ai/brain')
  async saveAuraBrain(@CurrentUser() user: User, @Body() body: any) {
    this.ensureAdmin(user);
    return this.adminService.saveAuraBrainConfig(body || {});
  }

  @Get('ai/brain/models')
  async getAuraBrainModels(
    @CurrentUser() user: User,
    @Query('provider') provider?: 'groq' | 'gemini' | 'custom',
    @Query('customBaseUrl') customBaseUrl?: string,
  ) {
    this.ensureModeratorOrAdmin(user);
    return this.adminService.fetchProviderModelsLive(provider || 'groq', customBaseUrl);
  }

  @Post('ai/brain/test')
  async testAuraBrain(@CurrentUser() user: User, @Body() body: any) {
    this.ensureAdmin(user);
    return this.adminService.testAuraBrainLive(body || {});
  }

  // --- CHAT CENTRAL DE ADMINISTRACIÓN CON AURA IA Y HERRAMIENTAS ---
  @Post('ai/chat')
  async chatWithAuraAdmin(@CurrentUser() user: User, @Body() body: { messages: any[] }) {
    this.ensureModeratorOrAdmin(user);
    return this.adminService.chatWithAuraAdmin(body.messages || [], user);
  }

  @Post('ai/execute-tool')
  async executeAuraAdminTool(
    @CurrentUser() user: User,
    @Body() body: { tool: string; params: Record<string, any> },
  ) {
    this.ensureAdmin(user);
    return this.adminService.executeAuraAdminTool(body.tool, body.params || {}, user);
  }

  // --- RANKING SEMANAL TOP 10 ---
  @Get('rankings/weekly')
  async getWeeklyRanking(@CurrentUser() user: User) {
    this.ensureModeratorOrAdmin(user);
    return this.adminService.getWeeklyRanking();
  }

  @Post('rankings/recalculate')
  async recalculateWeeklyRanking(@CurrentUser() user: User) {
    this.ensureAdmin(user);
    return this.adminService.recalculateWeeklyRanking();
  }

  @Put('rankings/weekly')
  async saveManualWeeklyRanking(
    @CurrentUser() user: User,
    @Body() body: { items: any[] },
  ) {
    this.ensureAdmin(user);
    return this.adminService.saveManualWeeklyRanking(body.items || []);
  }

  // --- COLECCIONES CURADAS IA ---
  @Get('collections')
  async getCuratedCollections(@CurrentUser() user: User) {
    this.ensureModeratorOrAdmin(user);
    return this.adminService.getCuratedCollections();
  }

  @Post('collections/regenerate')
  async regenerateCuratedCollections(@CurrentUser() user: User) {
    this.ensureAdmin(user);
    return this.adminService.regenerateCuratedCollections();
  }

  // --- NOTIFICACIONES PUSH Y REDACCIÓN IA ---
  @Post('push/send')
  async sendPush(
    @CurrentUser() user: User,
    @Body() body: GlobalPushPayload,
  ) {
    this.ensureAdmin(user);
    return this.adminService.sendPushNotification(
      body,
      user.displayName || user.username || 'Admin',
    );
  }

  @Get('push/history')
  async getPushHistory(@CurrentUser() user: User) {
    this.ensureModeratorOrAdmin(user);
    return this.adminService.getPushHistory();
  }

  @Post('ai/generate-push')
  async generatePushWithAi(
    @CurrentUser() user: User,
    @Body()
    body: {
      draftTitle?: string;
      draftMessage?: string;
      contentTitle?: string;
      contentType?: string;
      contextType?: 'banner' | 'push';
    },
  ) {
    this.ensureAdmin(user);
    return this.adminService.generatePushCopyWithAi(body);
  }

  // --- PURGA DE CHAT Y MANTENIMIENTO ---
  @Post('chat/purge')
  async purgeChat(
    @CurrentUser() user: User,
    @Body() body: { count?: number; olderThanDays?: number },
  ) {
    this.ensureModeratorOrAdmin(user);
    return this.adminService.purgeChatMessages(body || {});
  }
}
