export interface UserDeviceItem {
  deviceInfo: string | null;
  osVersion: string | null;
  appVersion: string | null;
  lastActive: string;
}

export interface UserStats {
  ratings: number;
  favorites: number;
  playbackProgress: number;
  comments: number;
  chatMessages?: number;
}

export interface UserListItem {
  id: string;
  email: string;
  username: string;
  displayName: string | null;
  avatarUrl: string | null;
  role: 'USER' | 'MODERATOR' | 'ADMIN';
  isOnline: boolean;
  lastSeen: string;
  deviceInfo: string | null;
  currentlyListening: string | null;
  createdAt: string;
  isBanned: boolean;
  banReason: string | null;
  accounts: { provider: string; providerId: string }[];
  devices: UserDeviceItem[];
  stats: UserStats;
}

export interface UsersPaginatedResponse {
  data: UserListItem[];
  meta: {
    total: number;
    page: number;
    limit: number;
    totalPages: number;
  };
}

export interface BanUserDto {
  targetUserId: string;
  reason: string;
  durationHours?: number;
}
