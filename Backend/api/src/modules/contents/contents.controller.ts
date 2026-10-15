import {
  Controller,
  Get,
  Post,
  Body,
  Param,
  Query,
  UseGuards,
  HttpCode,
  HttpStatus,
} from '@nestjs/common';
import { ContentsService } from './contents.service.js';
import { QueryContentsDto } from './dto/query-contents.dto.js';
import { CreateContentDto } from './dto/create-content.dto.js';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard.js';
import { Public } from '../auth/decorators/public.decorator.js';
import { CurrentUser } from '../auth/decorators/current-user.decorator.js';
import type { User } from '@prisma/client';

@Controller('api/v1/contents')
@UseGuards(JwtAuthGuard)
export class ContentsController {
  constructor(private readonly contentsService: ContentsService) {}

  @Public()
  @Get()
  async getContents(
    @Query() query: QueryContentsDto,
    @CurrentUser() user: User | null,
  ) {
    return this.contentsService.findAll(query, user?.id);
  }

  @Public()
  @Get('featured')
  async getFeatured() {
    return this.contentsService.getFeatured();
  }

  @Public()
  @Get('rankings')
  async getRankings() {
    return this.contentsService.getRankings();
  }

  @Public()
  @Get('categories')
  async getCategories() {
    return this.contentsService.getCategories();
  }

  @Public()
  @Get(':id')
  async getContent(
    @Param('id') id: string,
    @CurrentUser() user: User | null,
  ) {
    return this.contentsService.findByIdOrSlug(id, user?.id);
  }

  @Public()
  @Post(':id/view')
  @HttpCode(HttpStatus.OK)
  async incrementView(@Param('id') id: string) {
    await this.contentsService.incrementViews(id);
    return { success: true };
  }

  @Post()
  @HttpCode(HttpStatus.CREATED)
  async createContent(@Body() dto: CreateContentDto) {
    return this.contentsService.create(dto);
  }
}
