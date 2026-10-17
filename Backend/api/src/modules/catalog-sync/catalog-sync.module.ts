import { Global, Module } from '@nestjs/common';
import { CatalogSyncService } from './catalog-sync.service.js';
import { CatalogSyncController } from './catalog-sync.controller.js';

@Global()
@Module({
  controllers: [CatalogSyncController],
  providers: [CatalogSyncService],
  exports: [CatalogSyncService],
})
export class CatalogSyncModule {}
