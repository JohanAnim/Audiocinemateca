import { Module } from '@nestjs/common';
import { CommunityService } from './community.service.js';
import { CommunityController } from './community.controller.js';
import { ChatGateway } from './gateways/chat.gateway.js';
import { UsersModule } from '../users/users.module.js';
import { AuthModule } from '../auth/auth.module.js';

@Module({
  imports: [UsersModule, AuthModule],
  controllers: [CommunityController],
  providers: [CommunityService, ChatGateway],
  exports: [CommunityService, ChatGateway],
})
export class CommunityModule {}
