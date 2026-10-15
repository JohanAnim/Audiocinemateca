import { Role } from '@prisma/client';

export interface JwtPayload {
  sub: string;
  email: string;
  username: string;
  role: Role;
}
