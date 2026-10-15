import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { PrismaModule } from './prisma/prisma.module.js';
import { UsersModule } from './modules/users/users.module.js';
import { AuthModule } from './modules/auth/auth.module.js';
import { ContentsModule } from './modules/contents/contents.module.js';
import { ProgressModule } from './modules/progress/progress.module.js';
import { FavoritesModule } from './modules/favorites/favorites.module.js';
import { RatingsModule } from './modules/ratings/ratings.module.js';
import { CommentsModule } from './modules/comments/comments.module.js';
import { AppController } from './app.controller.js';
import { AppService } from './app.service.js';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: ['.env', '../.env'],
    }),
    PrismaModule,
    UsersModule,
    AuthModule,
    ContentsModule,
    ProgressModule,
    FavoritesModule,
    RatingsModule,
    CommentsModule,
  ],
  controllers: [AppController],
  providers: [AppService],
})
export class AppModule {}
