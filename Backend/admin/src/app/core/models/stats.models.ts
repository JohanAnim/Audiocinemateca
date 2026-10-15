export interface ExecutiveStats {
  users: {
    total: number;
    onlineNow: number;
  };
  platforms: {
    Android: number;
    Windows: number;
    macOS: number;
    Linux: number;
    Web: number;
    Otros: number;
  };
  catalog: {
    total: number;
    exclusive: number;
    byType: Record<string, number>;
    seasons: number;
    episodes: number;
  };
  engagement: {
    totalViews: number;
    totalRatings: number;
    averageRating: number;
    totalComments: number;
  };
  system: {
    uptimeSeconds: number;
    memoryRssMb: number;
    memoryHeapUsedMb: number;
    nodeVersion: string;
    serverTime: string;
  };
}
