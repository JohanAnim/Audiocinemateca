import { Module } from '@nestjs/common';
import { AppConfigService } from './app-config.service.js';
import { AppConfigController } from './app-config.controller.js';

@Module({
  controllers: [AppConfigController],
  providers: [AppConfigService],
  exports: [AppConfigService],
})
export class AppConfigModule {}
