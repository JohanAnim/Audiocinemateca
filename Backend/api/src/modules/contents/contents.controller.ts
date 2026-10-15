import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Body,
  Param,
  Query,
  UseGuards,
  HttpCode,
  HttpStatus,
  ForbiddenException,
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
  async createContent(
    @CurrentUser() user: User,
    @Body() dto: CreateContentDto,
  ) {
    if (user.role !== 'ADMIN' && user.role !== 'MODERATOR') {
      throw new ForbiddenException('Solo administradores pueden crear contenidos en el catálogo');
    }
    return this.contentsService.create(dto);
  }

  @Put(':id')
  async updateContent(
    @CurrentUser() user: User,
    @Param('id') id: string,
    @Body() dto: any,
  ) {
    if (user.role !== 'ADMIN') {
      throw new ForbiddenException('Solo administradores pueden modificar contenidos');
    }
    return this.contentsService.updateContent(id, dto);
  }

  @Delete(':id')
  async deleteContent(
    @CurrentUser() user: User,
    @Param('id') id: string,
  ) {
    if (user.role !== 'ADMIN') {
      throw new ForbiddenException('Solo administradores pueden eliminar contenidos del catálogo');
    }
    return this.contentsService.deleteContent(id);
  }
}
