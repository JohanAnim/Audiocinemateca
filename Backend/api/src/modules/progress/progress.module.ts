import { Module } from '@nestjs/common';
import { ProgressService } from './progress.service.js';
import { ProgressController } from './progress.controller.js';

@Module({
  controllers: [ProgressController],
  providers: [ProgressService],
  exports: [ProgressService],
})
export class ProgressModule {}
