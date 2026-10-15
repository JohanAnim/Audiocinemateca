import {
  Controller,
  Get,
  Put,
  Delete,
  Post,
  Param,
  Body,
  Query,
  UseGuards,
  ForbiddenException,
} from '@nestjs/common';
import { AppConfigService } from './app-config.service.js';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard.js';
import { Public } from '../auth/decorators/public.decorator.js';
import { CurrentUser } from '../auth/decorators/current-user.decorator.js';
import { UpsertConfigDto } from './dto/upsert-config.dto.js';
import { Role } from '@prisma/client';
import type { User } from '@prisma/client';

@Controller('api/v1/config')
@UseGuards(JwtAuthGuard)
export class AppConfigController {
  constructor(private readonly appConfigService: AppConfigService) {}

  // --- 1. CONFIGURACIÓN PÚBLICA INTEGRAL PARA LA APP ---
  @Public()
  @Get('app')
  async getPublicAppConfig() {
    return this.appConfigService.getPublicAppConfig();
  }

  // --- 2. CONTROL Y COMPROBACIÓN DE ACTUALIZACIONES ---
  @Public()
  @Get('version')
  async checkVersion(@Query('currentVersion') currentVersion?: string) {
    return this.appConfigService.checkVersion(currentVersion);
  }

  // --- 3. BANNER ACTIVO DEL FEED ---
  @Public()
  @Get('banner')
  async getActiveBanner() {
    return this.appConfigService.getActiveBanner();
  }

  // --- 4. LISTADO COMPLETO DE CONFIGURACIONES (SOLO ADMIN) ---
  @Get('all')
  async getAllConfigs(@CurrentUser() user: User) {
    if (user.role !== Role.ADMIN) {
      throw new ForbiddenException('Solo administradores pueden consultar todas las configuraciones');
    }
    return this.appConfigService.getAllConfigs();
  }

  // --- 5. OBTENER CONFIGURACIÓN ESPECÍFICA (SOLO ADMIN) ---
  @Get(':key')
  async getConfigByKey(@CurrentUser() user: User, @Param('key') key: string) {
    if (user.role !== Role.ADMIN) {
      throw new ForbiddenException('Solo administradores pueden consultar configuraciones');
    }
    const value = await this.appConfigService.getParsedConfig(key);
    return { key, value };
  }

  // --- 6. GUARDAR O EDITAR CONFIGURACIÓN (SOLO ADMIN) ---
  @Put(':key')
  async upsertConfig(
    @CurrentUser() user: User,
    @Param('key') key: string,
    @Body() dto: UpsertConfigDto,
  ) {
    if (user.role !== Role.ADMIN) {
      throw new ForbiddenException('Solo administradores pueden modificar la configuración del sistema');
    }
    return this.appConfigService.upsertConfig(key, dto.value, dto.description);
  }

  // --- 7. ELIMINAR CONFIGURACIÓN (SOLO ADMIN) ---
  @Delete(':key')
  async deleteConfig(@CurrentUser() user: User, @Param('key') key: string) {
    if (user.role !== Role.ADMIN) {
      throw new ForbiddenException('Solo administradores pueden eliminar configuraciones');
    }
    return this.appConfigService.deleteConfig(key);
  }

  // --- 8. RESTAURAR VALORES POR DEFECTO (SOLO ADMIN) ---
  @Post('seed')
  async seedDefaults(@CurrentUser() user: User) {
    if (user.role !== Role.ADMIN) {
      throw new ForbiddenException('Solo administradores pueden re-sembrar configuraciones');
    }
    await this.appConfigService.seedDefaultConfigs();
    return { success: true, message: 'Configuraciones por defecto restauradas' };
  }
}
