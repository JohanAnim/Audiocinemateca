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
import { CommentsService } from './comments.service.js';
import { CreateCommentDto } from './dto/create-comment.dto.js';
import { QueryCommentsDto } from './dto/query-comments.dto.js';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard.js';
import { Public } from '../auth/decorators/public.decorator.js';
import { CurrentUser } from '../auth/decorators/current-user.decorator.js';
import type { User } from '@prisma/client';

@Controller('api/v1/comments')
@UseGuards(JwtAuthGuard)
export class CommentsController {
  constructor(private readonly commentsService: CommentsService) {}

  @Post()
  @HttpCode(HttpStatus.CREATED)
  async addComment(
    @CurrentUser('id') userId: string,
    @Body() dto: CreateCommentDto,
  ) {
    return this.commentsService.addComment(userId, dto);
  }

  @Public()
  @Get()
  async getComments(
    @Query() query: QueryCommentsDto,
    @CurrentUser() user: User | null,
  ) {
    return this.commentsService.getComments(query, user?.id);
  }

  @Public()
  @Get('preview')
  async getPreview(
    @Query('contentId') contentId: string,
    @Query('part') partIndex?: number,
    @Query('episode') episodeIndex?: number,
  ) {
    return this.commentsService.getCommentsPreview(
      contentId,
      partIndex ? Number(partIndex) : 0,
      episodeIndex ? Number(episodeIndex) : 0,
    );
  }

  @Post(':id/like')
  @HttpCode(HttpStatus.OK)
  async toggleLike(
    @CurrentUser('id') userId: string,
    @Param('id') commentId: string,
  ) {
    return this.commentsService.toggleLike(userId, commentId);
  }

  @Delete(':id')
  async deleteComment(
    @CurrentUser() user: User,
    @Param('id') commentId: string,
  ) {
    return this.commentsService.deleteComment(user.id, commentId, user.role);
  }
}
