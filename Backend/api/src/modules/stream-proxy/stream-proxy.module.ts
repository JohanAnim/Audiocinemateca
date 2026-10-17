import { Module } from '@nestjs/common';
import { StreamProxyController } from './stream-proxy.controller.js';

@Module({
  controllers: [StreamProxyController],
})
export class StreamProxyModule {}
