import {
  Controller,
  Post,
  Get,
  Delete,
  Body,
  Param,
  Query,
  UseGuards,
  HttpCode,
  HttpStatus,
} from '@nestjs/common';
import { FavoritesService } from './favorites.service.js';
import { ToggleFavoriteDto } from './dto/toggle-favorite.dto.js';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard.js';
import { CurrentUser } from '../auth/decorators/current-user.decorator.js';
import type { User } from '@prisma/client';

@Controller('api/v1/favorites')
@UseGuards(JwtAuthGuard)
export class FavoritesController {
  constructor(private readonly favoritesService: FavoritesService) {}

  @Post('toggle')
  @HttpCode(HttpStatus.OK)
  async toggleFavorite(
    @CurrentUser('id') userId: string,
    @Body() dto: ToggleFavoriteDto,
  ) {
    return this.favoritesService.toggleFavorite(userId, dto.contentId);
  }

  @Get()
  async getFavorites(
    @CurrentUser('id') userId: string,
    @Query('page') page?: number,
    @Query('limit') limit?: number,
  ) {
    const p = page ? Number(page) : 1;
    const l = limit ? Math.min(Number(limit), 50) : 20;
    return this.favoritesService.getUserFavorites(userId, p, l);
  }

  @Get('check/:contentId')
  async checkFavorite(
    @CurrentUser('id') userId: string,
    @Param('contentId') contentId: string,
  ) {
    const isFavorite = await this.favoritesService.checkIsFavorite(userId, contentId);
    return { isFavorite };
  }

  @Delete(':contentId')
  async removeFavorite(
    @CurrentUser('id') userId: string,
    @Param('contentId') contentId: string,
  ) {
    return this.favoritesService.removeFavorite(userId, contentId);
  }

  @Delete()
  async clearFavorites(@CurrentUser('id') userId: string) {
    return this.favoritesService.clearAllFavorites(userId);
  }
}
