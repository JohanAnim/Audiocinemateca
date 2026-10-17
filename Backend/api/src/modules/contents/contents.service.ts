import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service.js';
import { AuraAiService } from '../ai/aura-ai.service.js';
import { QueryContentsDto, SortBy, SortOrder } from './dto/query-contents.dto.js';
import { CreateContentDto } from './dto/create-content.dto.js';
import { Prisma, ContentStatus } from '@prisma/client';

@Injectable()
export class ContentsService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly auraAiService: AuraAiService,
  ) {}

  async findAll(query: QueryContentsDto, userId?: string) {
    const page = query.page || 1;
    const limit = query.limit || 20;
    const skip = (page - 1) * limit;

    const where: Prisma.ContentWhereInput = {};

    if (query.status === 'ALL') {
      // No filtrar por status (usado por Admin Panel)
    } else if (
      query.status === 'DRAFT' ||
      query.status === 'PUBLISHED' ||
      query.status === 'ARCHIVED'
    ) {
      where.status = query.status as ContentStatus;
    } else {
      where.status = 'PUBLISHED';
    }

    if (query.isExclusive === 'true') {
      where.isExclusive = true;
    } else if (query.isExclusive === 'false') {
      where.isExclusive = false;
    }

    if (query.type) {
      where.type = query.type;
    }

    if (query.year) {
      where.releaseYear = query.year;
    }

    if (query.country) {
      where.country = { contains: query.country, mode: 'insensitive' };
    }

    if (query.genre) {
      where.OR = [
        {
          categories: {
            some: {
              category: {
                name: { contains: query.genre, mode: 'insensitive' },
              },
            },
          },
        },
        {
          genreRaw: { contains: query.genre, mode: 'insensitive' },
        },
      ];
    }

    if (query.search) {
      const searchTerms = query.search.trim();
      where.OR = [
        { title: { contains: searchTerms, mode: 'insensitive' } },
        { synopsis: { contains: searchTerms, mode: 'insensitive' } },
        { director: { contains: searchTerms, mode: 'insensitive' } },
        { narrator: { contains: searchTerms, mode: 'insensitive' } },
        { cast: { contains: searchTerms, mode: 'insensitive' } },
        { externalId: { contains: searchTerms, mode: 'insensitive' } },
      ];
    }

    // Ordenamiento dinámico
    let orderBy: Prisma.ContentOrderByWithRelationInput = { createdAt: 'desc' };
    const sortOrder = query.order === SortOrder.ASC ? 'asc' : 'desc';

    switch (query.sortBy) {
      case SortBy.POPULAR:
        orderBy = { viewsCount: sortOrder };
        break;
      case SortBy.RATING:
        orderBy = { averageRating: sortOrder };
        break;
      case SortBy.TITLE:
        orderBy = { title: sortOrder };
        break;
      case SortBy.YEAR:
        orderBy = { releaseYear: sortOrder };
        break;
      case SortBy.RECENT:
      default:
        orderBy = { createdAt: sortOrder };
        break;
    }

    const [total, items] = await Promise.all([
      this.prisma.content.count({ where }),
      this.prisma.content.findMany({
        where,
        skip,
        take: limit,
        orderBy,
        include: {
          categories: {
            include: { category: true },
          },
          seasons: {
            select: { id: true, seasonNumber: true },
          },
          ...(userId
            ? {
                favorites: {
                  where: { userId },
                  select: { id: true },
                },
              }
            : {}),
        },
      }),
    ]);

    const formattedItems = items.map((item: any) => ({
      ...item,
      categories: item.categories.map((c: any) => c.category),
      isFavorite: userId ? item.favorites?.length > 0 : false,
      favorites: undefined,
    }));

    return {
      data: formattedItems,
      meta: {
        total,
        page,
        limit,
        totalPages: Math.ceil(total / limit),
      },
    };
  }

  async findByIdOrSlug(identifier: string, userId?: string) {
    const content = await this.prisma.content.findFirst({
      where: {
        OR: [
          { id: identifier },
          { slug: identifier },
          { externalId: identifier },
          { externalId: `movie_${identifier}` },
          { externalId: `series_${identifier}` },
          { externalId: `doc_${identifier}` },
          { externalId: `short_${identifier}` },
        ],
      },
      include: {
        categories: {
          include: { category: true },
        },
        seasons: {
          orderBy: { seasonNumber: 'asc' },
          include: {
            episodes: {
              orderBy: { episodeNumber: 'asc' },
            },
          },
        },
        ...(userId
          ? {
              favorites: {
                where: { userId },
                select: { id: true },
              },
              playbackProgress: {
                where: { userId },
                select: {
                  positionMs: true,
                  durationMs: true,
                  isCompleted: true,
                  episodeId: true,
                  updatedAt: true,
                },
              },
              ratings: {
                where: { userId },
                select: { score: true, review: true },
              },
            }
          : {}),
      },
    });

    if (!content) {
      throw new NotFoundException(`Contenido '${identifier}' no encontrado.`);
    }

    return {
      ...content,
      categories: content.categories.map((c: any) => c.category),
      isFavorite: userId ? (content as any).favorites?.length > 0 : false,
      userRating: userId && (content as any).ratings?.length > 0 ? (content as any).ratings[0] : null,
      userProgress: userId ? (content as any).playbackProgress : [],
      favorites: undefined,
      ratings: undefined,
      playbackProgress: undefined,
    };
  }

  async getFeatured() {
    return this.prisma.content.findMany({
      where: { status: 'PUBLISHED' },
      take: 8,
      orderBy: [{ viewsCount: 'desc' }, { averageRating: 'desc' }],
      include: {
        categories: {
          include: { category: true },
        },
      },
    });
  }

  async getRankings() {
    const [topViewed, topRated, weeklyRanking] = await Promise.all([
      this.prisma.content.findMany({
        where: { status: 'PUBLISHED' },
        take: 10,
        orderBy: { viewsCount: 'desc' },
        include: {
          categories: { include: { category: true } },
        },
      }),
      this.prisma.content.findMany({
        where: { status: 'PUBLISHED', ratingsCount: { gte: 1 } },
        take: 10,
        orderBy: { averageRating: 'desc' },
        include: {
          categories: { include: { category: true } },
        },
      }),
      this.getWeeklyRanking(),
    ]);

    return {
      topViewed,
      topRated,
      weeklyTop10: weeklyRanking,
    };
  }

  async getWeeklyRanking() {
    const saved = await this.prisma.appConfig.findUnique({
      where: { key: 'weekly_top_10_ranking' },
    });
    if (saved) {
      try {
        return JSON.parse(saved.value);
      } catch {
        // continuar con cálculo dinámico
      }
    }
    return null;
  }

  async getCuratedCollections() {
    return this.auraAiService.getCuratedCollections();
  }

  async incrementViews(id: string): Promise<void> {
    await this.prisma.content.update({
      where: { id },
      data: {
        viewsCount: { increment: 1 },
      },
    });
  }

  async create(dto: CreateContentDto) {
    const baseSlug = dto.title
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-|-$)+/g, '');

    let slug = baseSlug;
    let counter = 1;
    while (await this.prisma.content.findUnique({ where: { slug } })) {
      slug = `${baseSlug}-${counter++}`;
    }

    // Manejo de categorías
    const categoryConnectOrCreate = (dto.categories || []).map((catName) => {
      const catSlug = catName.toLowerCase().replace(/[^a-z0-9]+/g, '-');
      return {
        category: {
          connectOrCreate: {
            where: { name: catName },
            create: { name: catName, slug: catSlug },
          },
        },
      };
    });

    return this.prisma.content.create({
      data: {
        externalId: dto.externalId,
        title: dto.title,
        slug,
        synopsis: dto.synopsis,
        coverUrl: dto.coverUrl,
        bannerUrl: dto.bannerUrl,
        type: dto.type,
        status: dto.status,
        releaseYear: dto.releaseYear,
        duration: dto.duration,
        durationMinutes: dto.durationMinutes,
        country: dto.country,
        director: dto.director,
        screenplay: dto.screenplay,
        music: dto.music,
        cinematography: dto.cinematography,
        cast: dto.cast,
        production: dto.production,
        narrator: dto.narrator,
        audioDescription: dto.audioDescription ?? true,
        dubbing: dto.dubbing,
        language: dto.language || 'es',
        isExclusive: !!dto.isExclusive,
        genreRaw: dto.genreRaw,
        filmaffinityUrl: dto.filmaffinityUrl,
        audioUrl: dto.audioUrl,
        audioUrls: dto.audioUrls || [],
        categories: {
          create: categoryConnectOrCreate,
        },
        seasons: dto.seasons
          ? {
              create: dto.seasons.map((s) => ({
                seasonNumber: s.seasonNumber,
                title: s.title,
                overview: s.overview,
                episodes: {
                  create: s.episodes.map((e) => ({
                    episodeNumber: e.episodeNumber,
                    title: e.title,
                    synopsis: e.synopsis,
                    audioUrl: e.audioUrl,
                    durationMinutes: e.durationMinutes,
                  })),
                },
              })),
            }
          : undefined,
      },
      include: {
        categories: { include: { category: true } },
        seasons: { include: { episodes: true } },
      },
    });
  }

  async updateContent(id: string, dto: any) {
    const existing = await this.prisma.content.findUnique({ where: { id } });
    if (!existing) {
      throw new NotFoundException(`Contenido con ID ${id} no encontrado`);
    }

    if (Array.isArray(dto.seasons)) {
      await this.prisma.season.deleteMany({ where: { contentId: id } });
    }

    if (Array.isArray(dto.categories)) {
      await this.prisma.contentCategory.deleteMany({ where: { contentId: id } });
    }

    const categoryConnectOrCreate = Array.isArray(dto.categories)
      ? dto.categories.map((catName: string) => {
          const catSlug = catName.toLowerCase().replace(/[^a-z0-9]+/g, '-');
          return {
            category: {
              connectOrCreate: {
                where: { name: catName },
                create: { name: catName, slug: catSlug },
              },
            },
          };
        })
      : undefined;

    return this.prisma.content.update({
      where: { id },
      data: {
        title: dto.title !== undefined ? dto.title : undefined,
        synopsis: dto.synopsis !== undefined ? dto.synopsis : undefined,
        coverUrl: dto.coverUrl !== undefined ? dto.coverUrl : undefined,
        bannerUrl: dto.bannerUrl !== undefined ? dto.bannerUrl : undefined,
        type: dto.type !== undefined ? dto.type : undefined,
        status: dto.status !== undefined ? dto.status : undefined,
        releaseYear: dto.releaseYear !== undefined ? dto.releaseYear : undefined,
        duration: dto.duration !== undefined ? dto.duration : undefined,
        durationMinutes: dto.durationMinutes !== undefined ? dto.durationMinutes : undefined,
        country: dto.country !== undefined ? dto.country : undefined,
        director: dto.director !== undefined ? dto.director : undefined,
        screenplay: dto.screenplay !== undefined ? dto.screenplay : undefined,
        music: dto.music !== undefined ? dto.music : undefined,
        cinematography: dto.cinematography !== undefined ? dto.cinematography : undefined,
        cast: dto.cast !== undefined ? dto.cast : undefined,
        production: dto.production !== undefined ? dto.production : undefined,
        narrator: dto.narrator !== undefined ? dto.narrator : undefined,
        audioDescription: dto.audioDescription !== undefined ? dto.audioDescription : undefined,
        dubbing: dto.dubbing !== undefined ? dto.dubbing : undefined,
        language: dto.language !== undefined ? dto.language : undefined,
        isExclusive: dto.isExclusive !== undefined ? dto.isExclusive : undefined,
        genreRaw: dto.genreRaw !== undefined ? dto.genreRaw : undefined,
        filmaffinityUrl: dto.filmaffinityUrl !== undefined ? dto.filmaffinityUrl : undefined,
        audioUrl: dto.audioUrl !== undefined ? dto.audioUrl : undefined,
        audioUrls: dto.audioUrls !== undefined ? dto.audioUrls : undefined,
        ...(categoryConnectOrCreate
          ? { categories: { create: categoryConnectOrCreate } }
          : {}),
        ...(Array.isArray(dto.seasons)
          ? {
              seasons: {
                create: dto.seasons.map((s: any) => ({
                  seasonNumber: s.seasonNumber,
                  title: s.title,
                  overview: s.overview,
                  episodes: {
                    create: (s.episodes || []).map((e: any) => ({
                      episodeNumber: e.episodeNumber,
                      title: e.title,
                      synopsis: e.synopsis,
                      audioUrl: e.audioUrl,
                      durationMinutes: e.durationMinutes,
                    })),
                  },
                })),
              },
            }
          : {}),
      },
      include: {
        categories: { include: { category: true } },
        seasons: { include: { episodes: true } },
      },
    });
  }

  async deleteContent(id: string) {
    const existing = await this.prisma.content.findUnique({ where: { id } });
    if (!existing) {
      throw new NotFoundException(`Contenido con ID ${id} no encontrado`);
    }

    await this.prisma.content.delete({ where: { id } });
    return { success: true, message: `Contenido "${existing.title}" eliminado correctamente` };
  }

  async getCategories() {
    const categories = await this.prisma.category.findMany({
      include: {
        _count: {
          select: { contents: true },
        },
      },
      orderBy: { name: 'asc' },
    });

    return categories.map((cat: any) => ({
      id: cat.id,
      name: cat.name,
      slug: cat.slug,
      description: cat.description,
      contentCount: cat._count.contents,
    }));
  }
}
