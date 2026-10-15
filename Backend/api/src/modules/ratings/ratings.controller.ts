import {
  Controller,
  Post,
  Get,
  Delete,
  Body,
  Param,
  UseGuards,
  HttpCode,
  HttpStatus,
} from '@nestjs/common';
import { RatingsService } from './ratings.service.js';
import { RateContentDto } from './dto/rate-content.dto.js';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard.js';
import { Public } from '../auth/decorators/public.decorator.js';
import { CurrentUser } from '../auth/decorators/current-user.decorator.js';
import type { User } from '@prisma/client';

@Controller('api/v1/ratings')
@UseGuards(JwtAuthGuard)
export class RatingsController {
  constructor(private readonly ratingsService: RatingsService) {}

  @Post()
  @HttpCode(HttpStatus.OK)
  async rateContent(
    @CurrentUser('id') userId: string,
    @Body() dto: RateContentDto,
  ) {
    return this.ratingsService.rateContent(userId, dto);
  }

  @Public()
  @Get(':contentId')
  async getRatingStats(
    @Param('contentId') contentId: string,
    @CurrentUser() user: User | null,
  ) {
    return this.ratingsService.getRatingStats(contentId, user?.id);
  }

  @Delete(':contentId')
  async removeRating(
    @CurrentUser('id') userId: string,
    @Param('contentId') contentId: string,
  ) {
    return this.ratingsService.removeRating(userId, contentId);
  }
}
