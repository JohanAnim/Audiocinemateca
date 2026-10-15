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
import { ProgressService } from './progress.service.js';
import { UpsertProgressDto } from './dto/upsert-progress.dto.js';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard.js';
import { CurrentUser } from '../auth/decorators/current-user.decorator.js';
import type { User } from '@prisma/client';

@Controller('api/v1/progress')
@UseGuards(JwtAuthGuard)
export class ProgressController {
  constructor(private readonly progressService: ProgressService) {}

  @Post()
  @HttpCode(HttpStatus.OK)
  async saveProgress(
    @CurrentUser('id') userId: string,
    @Body() dto: UpsertProgressDto,
  ) {
    return this.progressService.upsertProgress(userId, dto);
  }

  @Get('continue-listening')
  async getContinueListening(
    @CurrentUser('id') userId: string,
    @Query('limit') limit?: number,
  ) {
    const take = limit ? Math.min(Number(limit), 50) : 20;
    return this.progressService.getContinueListening(userId, take);
  }

  @Get(':contentId')
  async getProgressForContent(
    @CurrentUser('id') userId: string,
    @Param('contentId') contentId: string,
  ) {
    return this.progressService.getProgressForContent(userId, contentId);
  }

  @Delete(':contentId')
  async deleteContentProgress(
    @CurrentUser('id') userId: string,
    @Param('contentId') contentId: string,
    @Query('part') partIndex?: number,
    @Query('episode') episodeIndex?: number,
  ) {
    if (partIndex !== undefined && episodeIndex !== undefined) {
      return this.progressService.deleteProgress(
        userId,
        contentId,
        Number(partIndex),
        Number(episodeIndex),
      );
    }
    return this.progressService.deleteAllProgressForContent(userId, contentId);
  }

  @Delete()
  async clearAllProgress(@CurrentUser('id') userId: string) {
    return this.progressService.clearAll(userId);
  }
}
