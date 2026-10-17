import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service.js';
import { RateContentDto } from './dto/rate-content.dto.js';

@Injectable()
export class RatingsService {
  constructor(private readonly prisma: PrismaService) {}

  private async resolveContentId(contentIdOrExternal: string): Promise<string> {
    const content = await this.prisma.content.findFirst({
      where: {
        OR: [
          { id: contentIdOrExternal },
          { externalId: contentIdOrExternal },
          { externalId: `movie_${contentIdOrExternal}` },
          { externalId: `series_${contentIdOrExternal}` },
          { externalId: `doc_${contentIdOrExternal}` },
          { externalId: `short_${contentIdOrExternal}` },
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

  async rateContent(userId: string, dto: RateContentDto) {
    const contentId = await this.resolveContentId(dto.contentId);

    return this.prisma.$transaction(async (tx) => {
      // 1. Guardar o actualizar la calificación del usuario
      const rating = await tx.rating.upsert({
        where: {
          userId_contentId: {
            userId,
            contentId,
          },
        },
        update: {
          score: dto.score,
          review: dto.review,
          updatedAt: new Date(),
        },
        create: {
          userId,
          contentId,
          score: dto.score,
          review: dto.review,
        },
      });

      // 2. Recalcular promedio y total en vivo
      const stats = await tx.rating.aggregate({
        where: { contentId },
        _avg: { score: true },
        _count: { id: true },
      });

      const averageRating = Number((stats._avg.score || 0).toFixed(1));
      const ratingsCount = stats._count.id;

      // 3. Actualizar la tabla de contenidos
      await tx.content.update({
        where: { id: contentId },
        data: {
          averageRating,
          ratingsCount,
        },
      });

      return {
        ratingId: rating.id,
        contentId,
        score: rating.score,
        review: rating.review,
        averageRating,
        ratingsCount,
      };
    });
  }

  async getRatingStats(contentIdOrExternal: string, userId?: string) {
    const contentId = await this.resolveContentId(contentIdOrExternal);

    const [content, userRating] = await Promise.all([
      this.prisma.content.findUnique({
        where: { id: contentId },
        select: { averageRating: true, ratingsCount: true },
      }),
      userId
        ? this.prisma.rating.findUnique({
            where: {
              userId_contentId: {
                userId,
                contentId,
              },
            },
            select: { score: true, review: true },
          })
        : null,
    ]);

    if (!content) {
      throw new NotFoundException('Contenido no encontrado');
    }

    return {
      averageRating: content.averageRating,
      totalRatings: content.ratingsCount,
      userRating: userRating ? userRating.score : 0,
      userReview: userRating ? userRating.review : null,
    };
  }

  async removeRating(userId: string, contentIdOrExternal: string) {
    const contentId = await this.resolveContentId(contentIdOrExternal);

    return this.prisma.$transaction(async (tx) => {
      await tx.rating.deleteMany({
        where: {
          userId,
          contentId,
        },
      });

      const stats = await tx.rating.aggregate({
        where: { contentId },
        _avg: { score: true },
        _count: { id: true },
      });

      const averageRating = Number((stats._avg.score || 0).toFixed(1));
      const ratingsCount = stats._count.id;

      await tx.content.update({
        where: { id: contentId },
        data: {
          averageRating,
          ratingsCount,
        },
      });

      return { success: true, averageRating, ratingsCount };
    });
  }
}
