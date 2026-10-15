import { Role } from '@prisma/client';

export interface AuthResponse {
  user: {
    id: string;
    email: string;
    username: string;
    displayName: string | null;
    avatarUrl: string | null;
    role: Role;
    createdAt: Date;
  };
  tokens: {
    accessToken: string;
    refreshToken: string;
    expiresIn: string;
  };
}
