import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service.js';
import { UpsertProgressDto } from './dto/upsert-progress.dto.js';

@Injectable()
export class ProgressService {
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

  async upsertProgress(userId: string, dto: UpsertProgressDto) {
    const realContentId = await this.resolveContentId(dto.contentId);
    const partIndex = dto.partIndex ?? 0;
    const episodeIndex = dto.episodeIndex ?? 0;

    // Si la posición supera el 95% de la duración, marcar como completado
    const isCompleted =
      dto.isCompleted ??
      (dto.durationMs > 0 && dto.positionMs >= dto.durationMs * 0.95);

    const record = await this.prisma.playbackProgress.upsert({
      where: {
        userId_contentId_partIndex_episodeIndex: {
          userId,
          contentId: realContentId,
          partIndex,
          episodeIndex,
        },
      },
      update: {
        positionMs: BigInt(dto.positionMs),
        durationMs: BigInt(dto.durationMs),
        isCompleted,
        updatedAt: new Date(),
      },
      create: {
        userId,
        contentId: realContentId,
        partIndex,
        episodeIndex,
        positionMs: BigInt(dto.positionMs),
        durationMs: BigInt(dto.durationMs),
        isCompleted,
      },
    });

    return {
      id: record.id,
      contentId: record.contentId,
      partIndex: record.partIndex,
      episodeIndex: record.episodeIndex,
      positionMs: Number(record.positionMs),
      durationMs: Number(record.durationMs),
      isCompleted: record.isCompleted,
      updatedAt: record.updatedAt,
    };
  }

  async getContinueListening(userId: string, limit = 20) {
    const records = await this.prisma.playbackProgress.findMany({
      where: {
        userId,
        isCompleted: false,
        positionMs: { gt: BigInt(5000) }, // Más de 5 segundos reproducidos
      },
      orderBy: { updatedAt: 'desc' },
      take: limit,
      include: {
        content: {
          include: {
            categories: { include: { category: true } },
          },
        },
        episode: true,
      },
    });

    return records.map((r: any) => ({
      id: r.id,
      contentId: r.contentId,
      partIndex: r.partIndex,
      episodeIndex: r.episodeIndex,
      positionMs: Number(r.positionMs),
      durationMs: Number(r.durationMs),
      percentage:
        Number(r.durationMs) > 0
          ? Math.round((Number(r.positionMs) / Number(r.durationMs)) * 100)
          : 0,
      isCompleted: r.isCompleted,
      updatedAt: r.updatedAt,
      content: {
        ...r.content,
        categories: r.content.categories.map((c: any) => c.category),
      },
      episode: r.episode,
    }));
  }

  async getProgressForContent(userId: string, contentIdOrExternal: string) {
    const realContentId = await this.resolveContentId(contentIdOrExternal);

    const records = await this.prisma.playbackProgress.findMany({
      where: {
        userId,
        contentId: realContentId,
      },
      orderBy: [{ partIndex: 'asc' }, { episodeIndex: 'asc' }],
    });

    return records.map((r) => ({
      id: r.id,
      contentId: r.contentId,
      partIndex: r.partIndex,
      episodeIndex: r.episodeIndex,
      positionMs: Number(r.positionMs),
      durationMs: Number(r.durationMs),
      isCompleted: r.isCompleted,
      updatedAt: r.updatedAt,
    }));
  }

  async deleteProgress(
    userId: string,
    contentIdOrExternal: string,
    partIndex = 0,
    episodeIndex = 0,
  ) {
    const realContentId = await this.resolveContentId(contentIdOrExternal);

    await this.prisma.playbackProgress.deleteMany({
      where: {
        userId,
        contentId: realContentId,
        partIndex,
        episodeIndex,
      },
    });

    return { success: true };
  }

  async deleteAllProgressForContent(userId: string, contentIdOrExternal: string) {
    const realContentId = await this.resolveContentId(contentIdOrExternal);

    await this.prisma.playbackProgress.deleteMany({
      where: {
        userId,
        contentId: realContentId,
      },
    });

    return { success: true };
  }

  async clearAll(userId: string) {
    await this.prisma.playbackProgress.deleteMany({
      where: { userId },
    });

    return { success: true };
  }
}
