import {
  Controller,
  Post,
  Get,
  Patch,
  Body,
  UseGuards,
  HttpCode,
  HttpStatus,
} from '@nestjs/common';
import { AuthService } from './auth.service.js';
import { UsersService } from '../users/users.service.js';
import { RegisterDto } from './dto/register.dto.js';
import { LoginDto } from './dto/login.dto.js';
import { GoogleLoginDto } from './dto/google-login.dto.js';
import { KeycloakLoginDto } from './dto/keycloak-login.dto.js';
import { RefreshTokenDto } from './dto/refresh-token.dto.js';
import { JwtAuthGuard } from './guards/jwt-auth.guard.js';
import { Public } from './decorators/public.decorator.js';
import { CurrentUser } from './decorators/current-user.decorator.js';
import type { User } from '@prisma/client';

@Controller('api/v1/auth')
@UseGuards(JwtAuthGuard)
export class AuthController {
  constructor(
    private readonly authService: AuthService,
    private readonly usersService: UsersService,
  ) {}

  @Public()
  @Post('register')
  @HttpCode(HttpStatus.CREATED)
  async register(@Body() dto: RegisterDto) {
    return this.authService.register(dto);
  }

  @Public()
  @Post('login')
  @HttpCode(HttpStatus.OK)
  async login(@Body() dto: LoginDto) {
    return this.authService.loginLocal(dto);
  }

  @Public()
  @Post('google')
  @HttpCode(HttpStatus.OK)
  async loginGoogle(@Body() dto: GoogleLoginDto) {
    return this.authService.loginGoogle(dto);
  }

  @Public()
  @Post('keycloak')
  @HttpCode(HttpStatus.OK)
  async loginKeycloak(@Body() dto: KeycloakLoginDto) {
    return this.authService.loginKeycloak(dto);
  }

  @Public()
  @Post('refresh')
  @HttpCode(HttpStatus.OK)
  async refreshTokens(@Body() dto: RefreshTokenDto) {
    return this.authService.refreshTokens(dto);
  }

  @Get('me')
  async getProfile(@CurrentUser() user: User) {
    return {
      id: user.id,
      email: user.email,
      username: user.username,
      displayName: user.displayName,
      avatarUrl: user.avatarUrl,
      role: user.role,
      isOnline: user.isOnline,
      lastSeen: user.lastSeen,
      createdAt: user.createdAt,
    };
  }

  @Patch('profile')
  async updateProfile(
    @CurrentUser('id') userId: string,
    @Body() body: { displayName?: string; avatarUrl?: string },
  ) {
    const updated = await this.usersService.updateProfile(userId, body);
    return {
      id: updated.id,
      email: updated.email,
      username: updated.username,
      displayName: updated.displayName,
      avatarUrl: updated.avatarUrl,
      role: updated.role,
    };
  }

  @Post('presence')
  @HttpCode(HttpStatus.OK)
  async updatePresence(
    @CurrentUser('id') userId: string,
    @Body() body: { isOnline: boolean },
  ) {
    await this.usersService.updateOnlinePresence(userId, body.isOnline ?? true);
    return { success: true, isOnline: body.isOnline ?? true };
  }
}
