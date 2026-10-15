import { Controller, Get, UseGuards, ForbiddenException } from '@nestjs/common';
import { AdminService } from './admin.service.js';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard.js';
import { CurrentUser } from '../auth/decorators/current-user.decorator.js';
import { Role } from '@prisma/client';
import type { User } from '@prisma/client';

@Controller('api/v1/admin')
@UseGuards(JwtAuthGuard)
export class AdminController {
  constructor(private readonly adminService: AdminService) {}

  @Get('stats')
  async getStats(@CurrentUser() user: User) {
    if (user.role !== Role.ADMIN && user.role !== Role.MODERATOR) {
      throw new ForbiddenException('Acceso restringido a administradores');
    }
    return this.adminService.getExecutiveStats();
  }
}
