import {
  Controller,
  Get,
  Patch,
  Delete,
  Param,
  Query,
  Body,
  UseGuards,
  ForbiddenException,
} from '@nestjs/common';
import { UsersService } from './users.service.js';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard.js';
import { CurrentUser } from '../auth/decorators/current-user.decorator.js';
import { Role } from '@prisma/client';
import type { User } from '@prisma/client';

@Controller('api/v1/users')
@UseGuards(JwtAuthGuard)
export class UsersController {
  constructor(private readonly usersService: UsersService) {}

  @Get()
  async getUsers(
    @CurrentUser() currentUser: User,
    @Query('page') page?: number,
    @Query('limit') limit?: number,
    @Query('search') search?: string,
    @Query('role') role?: Role,
    @Query('isOnline') isOnline?: string,
  ) {
    if (currentUser.role !== Role.ADMIN && currentUser.role !== Role.MODERATOR) {
      throw new ForbiddenException('Acceso restringido a administradores y moderadores');
    }

    const onlineBool = isOnline === 'true' ? true : isOnline === 'false' ? false : undefined;

    return this.usersService.findAll({
      page,
      limit,
      search,
      role,
      isOnline: onlineBool,
    });
  }

  @Get(':id')
  async getUserDetail(
    @CurrentUser() currentUser: User,
    @Param('id') id: string,
  ) {
    if (currentUser.role !== Role.ADMIN && currentUser.role !== Role.MODERATOR) {
      throw new ForbiddenException('Acceso restringido a administradores y moderadores');
    }
    return this.usersService.getUserDetail(id);
  }

  @Patch(':id/role')
  async updateRole(
    @CurrentUser() currentUser: User,
    @Param('id') id: string,
    @Body('role') newRole: Role,
  ) {
    if (currentUser.role !== Role.ADMIN) {
      throw new ForbiddenException('Solo los administradores pueden cambiar roles de usuario');
    }
    return this.usersService.updateRole(id, newRole);
  }

  @Delete(':id')
  async deleteUser(
    @CurrentUser() currentUser: User,
    @Param('id') id: string,
  ) {
    if (currentUser.role !== Role.ADMIN) {
      throw new ForbiddenException('Solo los administradores pueden eliminar usuarios');
    }
    if (currentUser.id === id) {
      throw new ForbiddenException('No puedes eliminar tu propia cuenta de administrador');
    }
    return this.usersService.deleteUser(id);
  }
}
