import { Module } from '@nestjs/common';
import { FavoritesService } from './favorites.service.js';
import { FavoritesController } from './favorites.controller.js';

@Module({
  controllers: [FavoritesController],
  providers: [FavoritesService],
  exports: [FavoritesService],
})
export class FavoritesModule {}
