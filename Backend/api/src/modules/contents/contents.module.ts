import { Module } from '@nestjs/common';
import { ContentsService } from './contents.service.js';
import { ContentsController } from './contents.controller.js';

@Module({
  controllers: [ContentsController],
  providers: [ContentsService],
  exports: [ContentsService],
})
export class ContentsModule {}
