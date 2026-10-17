import {
  Controller,
  Get,
  Post,
  Body,
  Query,
  UseGuards,
  ForbiddenException,
} from '@nestjs/common';
import { CatalogSyncService } from './catalog-sync.service.js';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard.js';
import { CurrentUser } from '../auth/decorators/current-user.decorator.js';
import { Role } from '@prisma/client';
import type { User } from '@prisma/client';

@Controller('api/v1/catalog-sync')
@UseGuards(JwtAuthGuard)
export class CatalogSyncController {
  constructor(private readonly catalogSyncService: CatalogSyncService) {}

  private assertAdmin(user: User) {
    if (user.role !== Role.ADMIN && user.role !== Role.MODERATOR) {
      throw new ForbiddenException('Se requieren permisos de administrador');
    }
  }

  @Get('status')
  async getStatus(
    @CurrentUser() user: User,
    @Query('checkRemote') checkRemote?: string,
  ) {
    this.assertAdmin(user);
    return this.catalogSyncService.getCatalogStatus(checkRemote !== 'false');
  }

  @Post('check')
  async checkUpdates(
    @CurrentUser() user: User,
    @Body() body: { forcePush?: boolean },
  ) {
    this.assertAdmin(user);
    return this.catalogSyncService.checkCatalogUpdatesAutomatic(Boolean(body?.forcePush));
  }

  @Post('sync')
  async syncDatabase(
    @CurrentUser() user: User,
    @Body() body: { downloadRemote?: boolean; notifyPush?: boolean },
  ) {
    this.assertAdmin(user);
    return this.catalogSyncService.triggerFullDatabaseSync({
      downloadRemote: body?.downloadRemote ?? true,
      notifyPush: Boolean(body?.notifyPush),
    });
  }

  @Get('lookup')
  async lookupItem(
    @CurrentUser() user: User,
    @Query('q') query: string,
  ) {
    this.assertAdmin(user);
    const found = this.catalogSyncService.findItemInCatalog(query || '');
    return {
      found: Boolean(found),
      data: found,
    };
  }

  @Post('audit-links')
  async auditLinks(
    @CurrentUser() user: User,
    @Body() body: { sampleSize?: number },
  ) {
    this.assertAdmin(user);
    return this.catalogSyncService.auditSampleAudioLinks(body?.sampleSize || 15);
  }
}
