import {
  Injectable,
  UnauthorizedException,
  BadRequestException,
  Logger,
} from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import * as bcrypt from 'bcrypt';
import { OAuth2Client } from 'google-auth-library';
import { UsersService } from '../users/users.service.js';
import { RegisterDto } from './dto/register.dto.js';
import { LoginDto } from './dto/login.dto.js';
import { GoogleLoginDto } from './dto/google-login.dto.js';
import { KeycloakLoginDto } from './dto/keycloak-login.dto.js';
import { RefreshTokenDto } from './dto/refresh-token.dto.js';
import { JwtPayload } from './interfaces/jwt-payload.interface.js';
import { AuthResponse } from './interfaces/auth-response.interface.js';
import { AuthProvider, User } from '@prisma/client';

@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);
  private readonly googleClient: OAuth2Client;

  constructor(
    private readonly usersService: UsersService,
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
  ) {
    this.googleClient = new OAuth2Client();
  }

  // --- 1. REGISTRO LOCAL (EMAIL + CONTRASEÑA) ---
  async register(dto: RegisterDto): Promise<AuthResponse> {
    const saltRounds = 10;
    const passwordHash = await bcrypt.hash(dto.password, saltRounds);

    const user = await this.usersService.create({
      email: dto.email.toLowerCase().trim(),
      username: dto.username.toLowerCase().trim(),
      displayName: dto.displayName?.trim() || dto.username,
      avatarUrl: dto.avatarUrl,
      passwordHash,
      provider: AuthProvider.LOCAL,
      providerId: dto.email.toLowerCase().trim(),
    });

    const tokens = await this.generateTokens(user);
    return this.buildAuthResponse(user, tokens);
  }

  // --- 2. INICIO DE SESIÓN LOCAL (USUARIO/EMAIL + CONTRASEÑA) ---
  async loginLocal(dto: LoginDto): Promise<AuthResponse> {
    const identifier = dto.identifier.trim();
    const isEmail = identifier.includes('@');

    const user = isEmail
      ? await this.usersService.findByEmail(identifier.toLowerCase())
      : await this.usersService.findByUsername(identifier.toLowerCase());

    if (!user || !user.passwordHash) {
      throw new UnauthorizedException('Credenciales inválidas');
    }

    const isPasswordValid = await bcrypt.compare(dto.password, user.passwordHash);
    if (!isPasswordValid) {
      throw new UnauthorizedException('Credenciales inválidas');
    }

    // Vincular cuenta LOCAL si no existe todavía
    await this.usersService.linkAccount(user.id, AuthProvider.LOCAL, user.email);
    await this.usersService.updateLastSeen(user.id);

    const tokens = await this.generateTokens(user);
    return this.buildAuthResponse(user, tokens);
  }

  // --- 3. INICIO DE SESIÓN CON GOOGLE SIGN-IN ---
  async loginGoogle(dto: GoogleLoginDto): Promise<AuthResponse> {
    try {
      const ticket = await this.googleClient.verifyIdToken({
        idToken: dto.idToken,
      });

      const payload = ticket.getPayload();
      if (!payload || !payload.email || !payload.sub) {
        throw new BadRequestException('El token de Google no contiene la información requerida.');
      }

      const googleId = payload.sub;
      const email = payload.email.toLowerCase();
      const displayName = payload.name || payload.email.split('@')[0];
      const avatarUrl = payload.picture;

      // Buscar si ya existe por cuenta Google vinculada
      let user = await this.usersService.findByProvider(AuthProvider.GOOGLE, googleId);

      if (!user) {
        // Buscar si existe un usuario con el mismo email
        const existingEmailUser = await this.usersService.findByEmail(email);

        if (existingEmailUser) {
          // Asociar la cuenta de Google al usuario existente
          await this.usersService.linkAccount(existingEmailUser.id, AuthProvider.GOOGLE, googleId);
          user = existingEmailUser;
        } else {
          // Generar nombre de usuario único basado en el email
          let baseUsername = email.split('@')[0].replace(/[^a-zA-Z0-9_]/g, '').toLowerCase();
          if (baseUsername.length < 3) baseUsername = `user_${baseUsername}`;
          let username = baseUsername;
          let counter = 1;

          while (await this.usersService.findByUsername(username)) {
            username = `${baseUsername}${counter++}`;
          }

          // Crear nuevo usuario
          user = await this.usersService.create({
            email,
            username,
            displayName,
            avatarUrl,
            provider: AuthProvider.GOOGLE,
            providerId: googleId,
          });
        }
      }

      await this.usersService.updateLastSeen(user.id);
      const tokens = await this.generateTokens(user);
      return this.buildAuthResponse(user, tokens);
    } catch (error: any) {
      this.logger.error(`Error verificando token de Google: ${error.message}`);
      throw new UnauthorizedException('Token de Google inválido o expirado');
    }
  }

  // --- 4. INICIO DE SESIÓN CON KEYCLOAK (OIDC) ---
  async loginKeycloak(dto: KeycloakLoginDto): Promise<AuthResponse> {
    try {
      // Decodificar el token de Keycloak para extraer claims estándar
      const decoded: any = this.jwtService.decode(dto.token);
      if (!decoded || !decoded.sub) {
        throw new BadRequestException('Token de Keycloak inválido');
      }

      const keycloakId = decoded.sub;
      const email = (decoded.email || `${keycloakId}@keycloak.local`).toLowerCase();
      const displayName = decoded.name || decoded.preferred_username || 'Usuario';
      let username = (decoded.preferred_username || `kc_${keycloakId.slice(0, 8)}`).toLowerCase();

      let user = await this.usersService.findByProvider(AuthProvider.KEYCLOAK, keycloakId);

      if (!user) {
        const existingEmailUser = await this.usersService.findByEmail(email);
        if (existingEmailUser) {
          await this.usersService.linkAccount(existingEmailUser.id, AuthProvider.KEYCLOAK, keycloakId);
          user = existingEmailUser;
        } else {
          let baseUsername = username.replace(/[^a-zA-Z0-9_]/g, '');
          if (baseUsername.length < 3) baseUsername = `user_${baseUsername}`;
          let counter = 1;
          while (await this.usersService.findByUsername(username)) {
            username = `${baseUsername}${counter++}`;
          }

          user = await this.usersService.create({
            email,
            username,
            displayName,
            provider: AuthProvider.KEYCLOAK,
            providerId: keycloakId,
          });
        }
      }

      await this.usersService.updateLastSeen(user.id);
      const tokens = await this.generateTokens(user);
      return this.buildAuthResponse(user, tokens);
    } catch (error: any) {
      this.logger.error(`Error procesando token de Keycloak: ${error.message}`);
      throw new UnauthorizedException('No se pudo validar la sesión de Keycloak');
    }
  }

  // --- 5. REFRESCAR SESIÓN JWT ---
  async refreshTokens(dto: RefreshTokenDto): Promise<AuthResponse> {
    try {
      const refreshSecret =
        this.configService.get<string>('JWT_REFRESH_SECRET') ||
        'audiocinemateca_super_refresh_jwt_key_2026_dev_prod';

      const payload = this.jwtService.verify<JwtPayload>(dto.refreshToken, {
        secret: refreshSecret,
      });

      const user = await this.usersService.findById(payload.sub);
      if (!user) {
        throw new UnauthorizedException('Usuario no válido');
      }

      const tokens = await this.generateTokens(user);
      return this.buildAuthResponse(user, tokens);
    } catch {
      throw new UnauthorizedException('Refresh token expirado o inválido');
    }
  }

  // --- AUXILIARES: GENERACIÓN DE TOKENS JWT ---
  private async generateTokens(user: User) {
    const payload: JwtPayload = {
      sub: user.id,
      email: user.email,
      username: user.username,
      role: user.role,
    };

    const jwtSecret =
      this.configService.get<string>('JWT_SECRET') ||
      'audiocinemateca_super_secret_jwt_key_2026_dev_prod';
    const jwtExpiration = this.configService.get<string>('JWT_EXPIRATION') || '7d';

    const refreshSecret =
      this.configService.get<string>('JWT_REFRESH_SECRET') ||
      'audiocinemateca_super_refresh_jwt_key_2026_dev_prod';
    const refreshExpiration =
      this.configService.get<string>('JWT_REFRESH_EXPIRATION') || '30d';

    const [accessToken, refreshToken] = await Promise.all([
      this.jwtService.signAsync(payload, {
        secret: jwtSecret,
        expiresIn: jwtExpiration as any,
      }),
      this.jwtService.signAsync(payload, {
        secret: refreshSecret,
        expiresIn: refreshExpiration as any,
      }),
    ]);

    return {
      accessToken,
      refreshToken,
      expiresIn: jwtExpiration,
    };
  }

  private buildAuthResponse(user: User, tokens: { accessToken: string; refreshToken: string; expiresIn: string }): AuthResponse {
    return {
      user: {
        id: user.id,
        email: user.email,
        username: user.username,
        displayName: user.displayName,
        avatarUrl: user.avatarUrl,
        role: user.role,
        createdAt: user.createdAt,
      },
      tokens,
    };
  }
}
