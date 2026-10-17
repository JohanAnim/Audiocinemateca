import { Global, Module } from '@nestjs/common';
import { AuraAiService } from './aura-ai.service.js';

@Global()
@Module({
  providers: [AuraAiService],
  exports: [AuraAiService],
})
export class AiModule {}
