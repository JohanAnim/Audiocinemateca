import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service.js';

@Injectable()
export class FavoritesService {
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

  async toggleFavorite(userId: string, contentIdOrExternal: string) {
    const contentId = await this.resolveContentId(contentIdOrExternal);

    const existing = await this.prisma.favorite.findUnique({
      where: {
        userId_contentId: {
          userId,
          contentId,
        },
      },
    });

    if (existing) {
      await this.prisma.favorite.delete({
        where: { id: existing.id },
      });
      return { isFavorite: false, contentId };
    } else {
      await this.prisma.favorite.create({
        data: {
          userId,
          contentId,
        },
      });
      return { isFavorite: true, contentId };
    }
  }

  async getUserFavorites(userId: string, page = 1, limit = 20) {
    const skip = (page - 1) * limit;

    const [total, favorites] = await Promise.all([
      this.prisma.favorite.count({ where: { userId } }),
      this.prisma.favorite.findMany({
        where: { userId },
        skip,
        take: limit,
        orderBy: { createdAt: 'desc' },
        include: {
          content: {
            include: {
              categories: { include: { category: true } },
            },
          },
        },
      }),
    ]);

    const items = favorites.map((fav: any) => ({
      favoriteId: fav.id,
      addedAt: fav.createdAt,
      content: {
        ...fav.content,
        categories: fav.content.categories.map((c: any) => c.category),
        isFavorite: true,
      },
    }));

    return {
      data: items,
      meta: {
        total,
        page,
        limit,
        totalPages: Math.ceil(total / limit),
      },
    };
  }

  async checkIsFavorite(userId: string, contentIdOrExternal: string): Promise<boolean> {
    try {
      const contentId = await this.resolveContentId(contentIdOrExternal);
      const existing = await this.prisma.favorite.findUnique({
        where: {
          userId_contentId: {
            userId,
            contentId,
          },
        },
      });
      return !!existing;
    } catch {
      return false;
    }
  }

  async removeFavorite(userId: string, contentIdOrExternal: string) {
    const contentId = await this.resolveContentId(contentIdOrExternal);

    await this.prisma.favorite.deleteMany({
      where: {
        userId,
        contentId,
      },
    });

    return { success: true };
  }

  async clearAllFavorites(userId: string) {
    await this.prisma.favorite.deleteMany({
      where: { userId },
    });

    return { success: true };
  }
}
