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

  async updateOnlinePresence(
    userId: string,
    isOnline: boolean,
    deviceInfo?: string,
    currentlyListening?: string,
  ): Promise<void> {
    await this.prisma.user.update({
      where: { id: userId },
      data: {
        isOnline,
        lastSeen: new Date(),
        deviceInfo: deviceInfo !== undefined ? deviceInfo : undefined,
        currentlyListening: currentlyListening !== undefined ? currentlyListening : undefined,
      },
    });
  }

  async registerDevice(
    userId: string,
    data: { fcmToken: string; deviceInfo?: string; osVersion?: string; appVersion?: string },
  ) {
    await this.prisma.user.update({
      where: { id: userId },
      data: {
        fcmToken: data.fcmToken,
        deviceInfo: data.deviceInfo !== undefined ? data.deviceInfo : undefined,
      },
    });

    return this.prisma.userDevice.upsert({
      where: { fcmToken: data.fcmToken },
      update: {
        userId,
        deviceInfo: data.deviceInfo,
        osVersion: data.osVersion,
        appVersion: data.appVersion,
        lastActive: new Date(),
      },
      create: {
        userId,
        fcmToken: data.fcmToken,
        deviceInfo: data.deviceInfo,
        osVersion: data.osVersion,
        appVersion: data.appVersion,
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

  // --- MÉTODOS DE ADMINISTRACIÓN PARA PANEL ANGULAR ---

  async findAll(query: {
    page?: number;
    limit?: number;
    search?: string;
    role?: Role;
    isOnline?: boolean;
  }) {
    const page = query.page ? Number(query.page) : 1;
    const limit = query.limit ? Math.min(Number(query.limit), 100) : 20;
    const skip = (page - 1) * limit;

    const where: any = {};

    if (query.role) {
      where.role = query.role;
    }

    if (query.isOnline !== undefined) {
      where.isOnline = query.isOnline;
    }

    if (query.search) {
      const s = query.search.toLowerCase();
      where.OR = [
        { username: { contains: s, mode: 'insensitive' } },
        { email: { contains: s, mode: 'insensitive' } },
        { displayName: { contains: s, mode: 'insensitive' } },
      ];
    }

    const [total, users] = await Promise.all([
      this.prisma.user.count({ where }),
      this.prisma.user.findMany({
        where,
        skip,
        take: limit,
        orderBy: { createdAt: 'desc' },
        include: {
          accounts: { select: { provider: true, providerId: true } },
          devices: { select: { deviceInfo: true, osVersion: true, appVersion: true, lastActive: true } },
          bannedRecord: true,
          _count: {
            select: {
              ratings: true,
              favorites: true,
              playbackProgress: true,
              comments: true,
            },
          },
        },
      }),
    ]);

    return {
      data: users.map((u) => ({
        id: u.id,
        email: u.email,
        username: u.username,
        displayName: u.displayName,
        avatarUrl: u.avatarUrl,
        role: u.role,
        isOnline: u.isOnline,
        lastSeen: u.lastSeen,
        deviceInfo: u.deviceInfo,
        currentlyListening: u.currentlyListening,
        createdAt: u.createdAt,
        isBanned: !!u.bannedRecord,
        banReason: u.bannedRecord?.reason || null,
        accounts: u.accounts,
        devices: u.devices,
        stats: u._count,
      })),
      meta: {
        total,
        page,
        limit,
        totalPages: Math.ceil(total / limit),
      },
    };
  }

  async getUserDetail(id: string) {
    const user = await this.prisma.user.findUnique({
      where: { id },
      include: {
        accounts: true,
        devices: true,
        bannedRecord: true,
        _count: {
          select: {
            ratings: true,
            favorites: true,
            playbackProgress: true,
            comments: true,
            chatMessages: true,
          },
        },
      },
    });

    if (!user) {
      throw new NotFoundException(`Usuario con ID ${id} no encontrado`);
    }

    return {
      id: user.id,
      email: user.email,
      username: user.username,
      displayName: user.displayName,
      avatarUrl: user.avatarUrl,
      role: user.role,
      isOnline: user.isOnline,
      lastSeen: user.lastSeen,
      deviceInfo: user.deviceInfo,
      currentlyListening: user.currentlyListening,
      createdAt: user.createdAt,
      updatedAt: user.updatedAt,
      isBanned: !!user.bannedRecord,
      bannedRecord: user.bannedRecord,
      accounts: user.accounts,
      devices: user.devices,
      stats: user._count,
    };
  }

  async updateRole(id: string, newRole: Role) {
    const user = await this.findById(id);
    if (!user) {
      throw new NotFoundException(`Usuario ${id} no encontrado`);
    }

    return this.prisma.user.update({
      where: { id },
      data: { role: newRole },
      select: { id: true, username: true, email: true, role: true },
    });
  }

  async deleteUser(id: string) {
    const user = await this.findById(id);
    if (!user) {
      throw new NotFoundException(`Usuario ${id} no encontrado`);
    }

    await this.prisma.user.delete({ where: { id } });
    return { success: true, message: `Usuario @${user.username} eliminado correctamente` };
  }
}
