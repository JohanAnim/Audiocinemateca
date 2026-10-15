import { Injectable, ConflictException, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service.js';
import { AuthProvider, Role, User } from '@prisma/client';

@Injectable()
export class UsersService {
  constructor(private readonly prisma: PrismaService) {}

  async findById(id: string): Promise<User | null> {
    return this.prisma.user.findUnique({
      where: { id },
      include: { accounts: true },
    });
  }

  async findByEmail(email: string): Promise<User | null> {
    return this.prisma.user.findUnique({
      where: { email },
      include: { accounts: true },
    });
  }

  async findByUsername(username: string): Promise<User | null> {
    return this.prisma.user.findUnique({
      where: { username },
      include: { accounts: true },
    });
  }

  async findByProvider(provider: AuthProvider, providerId: string): Promise<User | null> {
    const account = await this.prisma.authAccount.findUnique({
      where: {
        provider_providerId: {
          provider,
          providerId,
        },
      },
      include: {
        user: {
          include: { accounts: true },
        },
      },
    });

    return account ? account.user : null;
  }

  async create(data: {
    email: string;
    username: string;
    displayName?: string;
    avatarUrl?: string;
    passwordHash?: string;
    role?: Role;
    provider: AuthProvider;
    providerId: string;
  }): Promise<User> {
    const existingEmail = await this.findByEmail(data.email);
    if (existingEmail) {
      throw new ConflictException('El correo electrónico ya está registrado.');
    }

    const existingUsername = await this.findByUsername(data.username);
    if (existingUsername) {
      throw new ConflictException('El nombre de usuario ya está en uso.');
    }

    return this.prisma.user.create({
      data: {
        email: data.email,
        username: data.username,
        displayName: data.displayName || data.username,
        avatarUrl: data.avatarUrl,
        passwordHash: data.passwordHash,
        role: data.role || Role.USER,
        accounts: {
          create: {
            provider: data.provider,
            providerId: data.providerId,
          },
        },
      },
      include: { accounts: true },
    });
  }

  async linkAccount(userId: string, provider: AuthProvider, providerId: string): Promise<void> {
    const existing = await this.prisma.authAccount.findUnique({
      where: {
        provider_providerId: {
          provider,
          providerId,
        },
      },
    });

    if (existing) {
      if (existing.userId !== userId) {
        throw new ConflictException('Esta cuenta externa ya está vinculada a otro usuario.');
      }
      return;
    }

    await this.prisma.authAccount.create({
      data: {
        userId,
        provider,
        providerId,
      },
    });
  }

  async updateLastSeen(userId: string): Promise<void> {
    await this.prisma.user.update({
      where: { id: userId },
      data: { lastSeen: new Date() },
    });
  }

  async updateOnlinePresence(userId: string, isOnline: boolean): Promise<void> {
    await this.prisma.user.update({
      where: { id: userId },
      data: {
        isOnline,
        lastSeen: new Date(),
      },
    });
  }

  async updateProfile(
    userId: string,
    data: { displayName?: string; avatarUrl?: string },
  ): Promise<User> {
    const user = await this.findById(userId);
    if (!user) {
      throw new NotFoundException('Usuario no encontrado');
    }

    return this.prisma.user.update({
      where: { id: userId },
      data: {
        displayName: data.displayName ?? user.displayName,
        avatarUrl: data.avatarUrl ?? user.avatarUrl,
      },
      include: { accounts: true },
    });
  }
}
