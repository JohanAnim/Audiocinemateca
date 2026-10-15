import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service.js';

@Injectable()
export class AdminService {
  constructor(private readonly prisma: PrismaService) {}

  async getExecutiveStats() {
    const [
      totalUsers,
      onlineUsers,
      totalContents,
      contentsByType,
      exclusiveCount,
      totalSeasons,
      totalEpisodes,
      totalComments,
      totalRatings,
      viewsAggregate,
      ratingsAggregate,
      devices,
    ] = await Promise.all([
      this.prisma.user.count(),
      this.prisma.user.count({ where: { isOnline: true } }),
      this.prisma.content.count(),
      this.prisma.content.groupBy({
        by: ['type'],
        _count: { _all: true },
      }),
      this.prisma.content.count({ where: { isExclusive: true } }),
      this.prisma.season.count(),
      this.prisma.episode.count(),
      this.prisma.comment.count(),
      this.prisma.rating.count(),
      this.prisma.content.aggregate({
        _sum: { viewsCount: true },
      }),
      this.prisma.content.aggregate({
        _avg: { averageRating: true },
      }),
      this.prisma.userDevice.findMany({
        select: { deviceInfo: true, osVersion: true },
      }),
    ]);

    // Calcular distribución de plataformas
    const platformCounts: Record<string, number> = {
      Android: 0,
      Windows: 0,
      macOS: 0,
      Linux: 0,
      Web: 0,
      Otros: 0,
    };

    devices.forEach((d) => {
      const info = (d.deviceInfo || '').toLowerCase();
      const os = (d.osVersion || '').toLowerCase();
      if (info.includes('android') || os.includes('android')) {
        platformCounts.Android++;
      } else if (info.includes('windows') || os.includes('windows')) {
        platformCounts.Windows++;
      } else if (info.includes('mac') || info.includes('darwin')) {
        platformCounts.macOS++;
      } else if (info.includes('linux')) {
        platformCounts.Linux++;
      } else if (info.includes('web') || info.includes('browser')) {
        platformCounts.Web++;
      } else {
        platformCounts.Otros++;
      }
    });

    const memoryUsage = process.memoryUsage();

    return {
      users: {
        total: totalUsers,
        onlineNow: onlineUsers,
      },
      platforms: platformCounts,
      catalog: {
        total: totalContents,
        exclusive: exclusiveCount,
        byType: contentsByType.reduce((acc, curr) => {
          acc[curr.type] = curr._count._all;
          return acc;
        }, {} as Record<string, number>),
        seasons: totalSeasons,
        episodes: totalEpisodes,
      },
      engagement: {
        totalViews: viewsAggregate._sum.viewsCount || 0,
        totalRatings,
        averageRating: Number((ratingsAggregate._avg.averageRating || 0).toFixed(2)),
        totalComments,
      },
      system: {
        uptimeSeconds: Math.floor(process.uptime()),
        memoryRssMb: Math.round(memoryUsage.rss / 1024 / 1024),
        memoryHeapUsedMb: Math.round(memoryUsage.heapUsed / 1024 / 1024),
        nodeVersion: process.version,
        serverTime: new Date().toISOString(),
      },
    };
  }
}
