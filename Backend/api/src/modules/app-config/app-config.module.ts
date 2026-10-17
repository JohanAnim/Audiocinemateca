import { Module, Global } from '@nestjs/common';
import { AppConfigService } from './app-config.service.js';
import { AppConfigController } from './app-config.controller.js';

@Global()
@Module({
  controllers: [AppConfigController],
  providers: [AppConfigService],
  exports: [AppConfigService],
})
export class AppConfigModule {}
