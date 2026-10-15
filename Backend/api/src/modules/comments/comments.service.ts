import {
  Injectable,
  NotFoundException,
  ForbiddenException,
} from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service.js';
import { CreateCommentDto } from './dto/create-comment.dto.js';
import { QueryCommentsDto } from './dto/query-comments.dto.js';
import { Role } from '@prisma/client';

@Injectable()
export class CommentsService {
  constructor(private readonly prisma: PrismaService) {}

  private async resolveContentId(contentIdOrExternal: string): Promise<string> {
    const content = await this.prisma.content.findFirst({
      where: {
        OR: [
          { id: contentIdOrExternal },
          { externalId: contentIdOrExternal },
          { slug: contentIdOrExternal },
        ],
      },
      select: { id: true },
    });

    if (!content) {
      throw new NotFoundException(`Contenido '${contentIdOrExternal}' no encontrado.`);
    }

    return content.id;
  }

  async addComment(userId: string, dto: CreateCommentDto) {
    const contentId = await this.resolveContentId(dto.contentId);

    const comment = await this.prisma.comment.create({
      data: {
        userId,
        contentId,
        partIndex: dto.partIndex ?? 0,
        episodeIndex: dto.episodeIndex ?? 0,
        commentText: dto.commentText.trim(),
      },
      include: {
        user: {
          select: {
            id: true,
            username: true,
            displayName: true,
            avatarUrl: true,
            role: true,
          },
        },
      },
    });

    return {
      id: comment.id,
      contentId: comment.contentId,
      partIndex: comment.partIndex,
      episodeIndex: comment.episodeIndex,
      commentText: comment.commentText,
      likesCount: 0,
      isLiked: false,
      createdAt: comment.createdAt,
      user: comment.user,
    };
  }

  async getComments(query: QueryCommentsDto, userId?: string) {
    const contentId = await this.resolveContentId(query.contentId);
    const page = query.page || 1;
    const limit = query.limit || 20;
    const skip = (page - 1) * limit;

    const where: any = { contentId };
    if (query.partIndex !== undefined) {
      where.partIndex = query.partIndex;
    }
    if (query.episodeIndex !== undefined) {
      where.episodeIndex = query.episodeIndex;
    }

    const [total, comments] = await Promise.all([
      this.prisma.comment.count({ where }),
      this.prisma.comment.findMany({
        where,
        skip,
        take: limit,
        orderBy: { createdAt: 'desc' },
        include: {
          user: {
            select: {
              id: true,
              username: true,
              displayName: true,
              avatarUrl: true,
              role: true,
            },
          },
          ...(userId
            ? {
                likes: {
                  where: { userId },
                  select: { id: true },
                },
              }
            : {}),
        },
      }),
    ]);

    const formatted = comments.map((c: any) => ({
      id: c.id,
      contentId: c.contentId,
      partIndex: c.partIndex,
      episodeIndex: c.episodeIndex,
      commentText: c.commentText,
      likesCount: c.likesCount,
      isLiked: userId ? (c.likes?.length > 0) : false,
      createdAt: c.createdAt,
      user: c.user,
    }));

    return {
      data: formatted,
      meta: {
        total,
        page,
        limit,
        totalPages: Math.ceil(total / limit),
      },
    };
  }

  async getCommentsPreview(
    contentIdOrExternal: string,
    partIndex = 0,
    episodeIndex = 0,
  ) {
    const contentId = await this.resolveContentId(contentIdOrExternal);

    const where = {
      contentId,
      partIndex,
      episodeIndex,
    };

    const [total, latest] = await Promise.all([
      this.prisma.comment.count({ where }),
      this.prisma.comment.findFirst({
        where,
        orderBy: { createdAt: 'desc' },
        include: {
          user: {
            select: {
              id: true,
              username: true,
              displayName: true,
              avatarUrl: true,
            },
          },
        },
      }),
    ]);

    return {
      totalComments: total,
      latestComment: latest
        ? {
            id: latest.id,
            commentText: latest.commentText,
            createdAt: latest.createdAt,
            user: latest.user,
          }
        : null,
    };
  }

  async toggleLike(userId: string, commentId: string) {
    const comment = await this.prisma.comment.findUnique({
      where: { id: commentId },
    });

    if (!comment) {
      throw new NotFoundException('Comentario no encontrado');
    }

    return this.prisma.$transaction(async (tx) => {
      const existing = await tx.commentLike.findUnique({
        where: {
          commentId_userId: {
            commentId,
            userId,
          },
        },
      });

      if (existing) {
        await tx.commentLike.delete({ where: { id: existing.id } });
        const updated = await tx.comment.update({
          where: { id: commentId },
          data: { likesCount: { decrement: 1 } },
          select: { likesCount: true },
        });
        return { isLiked: false, likesCount: Math.max(0, updated.likesCount) };
      } else {
        await tx.commentLike.create({
          data: { commentId, userId },
        });
        const updated = await tx.comment.update({
          where: { id: commentId },
          data: { likesCount: { increment: 1 } },
          select: { likesCount: true },
        });
        return { isLiked: true, likesCount: updated.likesCount };
      }
    });
  }

  async deleteComment(userId: string, commentId: string, role: Role) {
    const comment = await this.prisma.comment.findUnique({
      where: { id: commentId },
    });

    if (!comment) {
      throw new NotFoundException('Comentario no encontrado');
    }

    // Permitir si es el autor o un moderador/admin
    if (comment.userId !== userId && role !== Role.ADMIN && role !== Role.MODERATOR) {
      throw new ForbiddenException('No tienes permisos para eliminar este comentario');
    }

    await this.prisma.comment.delete({
      where: { id: commentId },
    });

    return { success: true };
  }
}
