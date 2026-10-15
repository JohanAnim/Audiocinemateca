import {
  WebSocketGateway,
  WebSocketServer,
  SubscribeMessage,
  OnGatewayInit,
  OnGatewayConnection,
  OnGatewayDisconnect,
  MessageBody,
  ConnectedSocket,
} from '@nestjs/websockets';
import { Server, Socket } from 'socket.io';
import { Logger } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { CommunityService } from '../community.service.js';
import { UsersService } from '../../users/users.service.js';
import { JwtPayload } from '../../auth/interfaces/jwt-payload.interface.js';
import { Role } from '@prisma/client';

interface ConnectedUser {
  socketId: string;
  id: string;
  username: string;
  displayName: string | null;
  avatarUrl: string | null;
  role: Role;
}

@WebSocketGateway({
  cors: {
    origin: '*',
    methods: ['GET', 'POST'],
    credentials: true,
  },
  namespace: '/ws/chat',
})
export class ChatGateway
  implements OnGatewayInit, OnGatewayConnection, OnGatewayDisconnect
{
  @WebSocketServer()
  server!: Server;

  private readonly logger = new Logger(ChatGateway.name);
  private readonly connectedUsers = new Map<string, ConnectedUser>();

  constructor(
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
    private readonly communityService: CommunityService,
    private readonly usersService: UsersService,
  ) {}

  afterInit(server: Server) {
    this.logger.log('🔌 ChatGateway inicializado con WebSockets');

    // Middleware de autenticación Socket.IO previo a la conexión
    server.use(async (socket: Socket, next) => {
      try {
        const authHeader =
          socket.handshake.auth?.token ||
          socket.handshake.headers?.authorization?.replace('Bearer ', '');

        if (!authHeader) {
          return next(new Error('Autenticación requerida'));
        }

        const jwtSecret =
          this.configService.get<string>('JWT_SECRET') ||
          'audiocinemateca_super_secret_jwt_key_2026_dev_prod';

        const payload = this.jwtService.verify<JwtPayload>(authHeader, {
          secret: jwtSecret,
        });

        const user = await this.usersService.findById(payload.sub);
        if (!user) {
          return next(new Error('Usuario no encontrado'));
        }

        const banCheck = await this.communityService.isUserBanned(user.id);
        if (banCheck.banned) {
          return next(new Error(`Usuario suspendido: ${banCheck.reason}`));
        }

        const connectedUser: ConnectedUser = {
          socketId: socket.id,
          id: user.id,
          username: user.username,
          displayName: user.displayName,
          avatarUrl: user.avatarUrl,
          role: user.role,
        };

        (socket as any).user = connectedUser;
        next();
      } catch (err: any) {
        return next(new Error(`Error de autenticación: ${err.message}`));
      }
    });
  }

  async handleConnection(socket: Socket) {
    const user = (socket as any).user as ConnectedUser | undefined;
    if (user) {
      user.socketId = socket.id;
      this.connectedUsers.set(user.id, user);

      await this.usersService.updateOnlinePresence(user.id, true);
      this.logger.log(`🟢 Usuario conectado al chat: ${user.username} (${socket.id})`);

      this.broadcastOnlineUsers();
    }
  }

  async handleDisconnect(socket: Socket) {
    const user = (socket as any).user as ConnectedUser | undefined;
    if (user) {
      this.connectedUsers.delete(user.id);
      await this.usersService.updateOnlinePresence(user.id, false);
      this.logger.log(`🔴 Usuario desconectado del chat: ${user.username}`);
      this.broadcastOnlineUsers();
    }
  }

  // --- EVENTO 1: ENVIAR MENSAJE EN TIEMPO REAL ---
  @SubscribeMessage('send_message')
  async handleSendMessage(
    @ConnectedSocket() socket: Socket,
    @MessageBody() rawData: any,
  ) {
    const user = (socket as any).user as ConnectedUser | undefined;
    if (!user) {
      this.logger.warn('Intento de envío de mensaje sin usuario autenticado en socket');
      return;
    }

    const payload = typeof rawData === 'string' ? JSON.parse(rawData) : rawData;
    const content = payload?.content?.trim();

    if (!content) {
      return;
    }

    this.logger.log(`💬 Mensaje de ${user.username}: ${content}`);

    try {
      const message = await this.communityService.saveMessage(user.id, content);

      // Emitir mensaje en vivo a todos los clientes del namespace
      socket.nsp.emit('new_message', message);
    } catch (error: any) {
      this.logger.error(`Error guardando mensaje: ${error.message}`);
      socket.emit('error', { message: error.message });
    }
  }

  // --- EVENTO 2: INDICADOR DE ESCRITURA ---
  @SubscribeMessage('typing')
  handleTyping(
    @ConnectedSocket() socket: Socket,
    @MessageBody() rawData: any,
  ) {
    const user = (socket as any).user as ConnectedUser | undefined;
    if (user) {
      const payload = typeof rawData === 'string' ? JSON.parse(rawData) : rawData;
      socket.broadcast.emit('user_typing', {
        userId: user.id,
        username: user.displayName || user.username,
        isTyping: !!payload?.isTyping,
      });
    }
  }

  // --- EVENTO 3: ELIMINAR MENSAJE ---
  @SubscribeMessage('delete_message')
  async handleDeleteMessage(
    @ConnectedSocket() socket: Socket,
    @MessageBody() rawData: any,
  ) {
    const user = (socket as any).user as ConnectedUser | undefined;
    if (!user) {
      return;
    }

    const payload = typeof rawData === 'string' ? JSON.parse(rawData) : rawData;
    const messageId = payload?.messageId;
    if (!messageId) {
      return;
    }

    try {
      await this.communityService.deleteMessage(user.id, messageId, user.role);
      socket.nsp.emit('message_deleted', { messageId });
    } catch (error: any) {
      socket.emit('error', { message: error.message });
    }
  }

  // --- AUXILIAR: EMITIR USUARIOS EN LÍNEA ---
  private broadcastOnlineUsers() {
    const users = Array.from(this.connectedUsers.values());
    const payload = {
      count: users.length,
      users: users.map((u) => ({
        id: u.id,
        username: u.username,
        displayName: u.displayName,
        avatarUrl: u.avatarUrl,
        role: u.role,
      })),
    };

    if (this.server) {
      try {
        this.server.of('/ws/chat').emit('online_users', payload);
      } catch {
        this.server.emit('online_users', payload);
      }
    }
  }

  getOnlineUsersList(): ConnectedUser[] {
    return Array.from(this.connectedUsers.values());
  }
}
