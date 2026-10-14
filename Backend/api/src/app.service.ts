import { Injectable } from '@nestjs/common';
import { PrismaService } from './prisma/prisma.service.js';

@Injectable()
export class AppService {
  constructor(private readonly prisma: PrismaService) {}

  async getHealthStatus() {
    let dbStatus = 'healthy';
    let dbLatencyMs = 0;

    try {
      const start = Date.now();
      await this.prisma.$queryRaw`SELECT 1`;
      dbLatencyMs = Date.now() - start;
    } catch {
      dbStatus = 'unreachable';
    }

    return {
      name: 'Audiocinemateca API',
      version: '1.0.0',
      status: dbStatus === 'healthy' ? 'operational' : 'degraded',
      timestamp: new Date().toISOString(),
      database: {
        engine: 'PostgreSQL 16',
        status: dbStatus,
        latencyMs: dbLatencyMs,
      },
      uptime: process.uptime(),
    };
  }
}
