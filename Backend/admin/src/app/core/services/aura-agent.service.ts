import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from './api.service';
import { Observable } from 'rxjs';

export interface AdminAgentToolProposal {
  tool:
    | 'send_push_notification'
    | 'publish_banner'
    | 'create_exclusive_content'
    | 'publish_announcement'
    | 'send_global_chat_message'
    | 'regenerate_collections'
    | 'recalculate_weekly_ranking'
    | 'sync_catalog'
    | 'toggle_maintenance';
  mode: 'propose' | 'execute';
  summary: string;
  params: Record<string, any>;
}

export interface AdminChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: number;
  proposal?: AdminAgentToolProposal | null;
  executedResult?: any | null;
  isExecuting?: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class AuraAgentService {
  private readonly api = inject(ApiService);

  readonly isOpen = signal<boolean>(false);
  readonly isSending = signal<boolean>(false);
  readonly messages = signal<AdminChatMessage[]>([]);

  constructor() {
    this.restoreSessionMessages();
  }

  toggleChat() {
    this.isOpen.update((v) => !v);
  }

  openChat() {
    this.isOpen.set(true);
  }

  closeChat() {
    this.isOpen.set(false);
  }

  clearChat() {
    this.messages.set([
      {
        id: 'welcome',
        role: 'assistant',
        content:
          '¡Hola! Soy Aura, tu agente oficial y copiloto en el Panel de Control. Puedo ayudarte a redactar y enviar notificaciones Push (recomendando películas o series del catálogo), publicar el Banner Destacado en el inicio, crear nuevas fichas de Contenido Exclusivo, publicar comunicados oficiales, regenerar colecciones temáticas o consultar el catálogo. ¿Qué deseas hacer hoy?',
        timestamp: Date.now(),
      },
    ]);
    this.persistMessages();
  }

  sendMessage(text: string): Observable<any> {
    const userMsg: AdminChatMessage = {
      id: `usr_${Date.now()}`,
      role: 'user',
      content: text.trim(),
      timestamp: Date.now(),
    };

    const currentList = [...this.messages(), userMsg];
    this.messages.set(currentList);
    this.persistMessages();
    this.isSending.set(true);

    const payloadMessages = currentList.map((m) => ({
      role: m.role,
      content: m.content,
      proposal: m.proposal,
      executed: Boolean(m.executedResult),
    }));

    return new Observable((subscriber) => {
      this.api.post<any>('admin/ai/chat', { messages: payloadMessages }).subscribe({
        next: (res) => {
          this.isSending.set(false);
          const assistantMsg: AdminChatMessage = {
            id: `aura_${Date.now()}`,
            role: 'assistant',
            content: res.reply || 'He procesado tu solicitud.',
            timestamp: Date.now(),
            proposal: res.proposal || null,
            executedResult: res.executedResult || null,
          };
          this.messages.update((list) => [...list, assistantMsg]);
          this.persistMessages();
          subscriber.next(res);
          subscriber.complete();
        },
        error: (err) => {
          this.isSending.set(false);
          const errorMsg: AdminChatMessage = {
            id: `err_${Date.now()}`,
            role: 'assistant',
            content: `⚠️ Error al procesar: ${err?.error?.message || err?.message || 'No se pudo conectar con el cerebro de Aura.'}`,
            timestamp: Date.now(),
          };
          this.messages.update((list) => [...list, errorMsg]);
          this.persistMessages();
          subscriber.error(err);
        },
      });
    });
  }

  executeProposal(msg: AdminChatMessage): Observable<any> {
    if (!msg.proposal) {
      throw new Error('No hay propuesta para ejecutar en este mensaje');
    }

    msg.isExecuting = true;
    this.messages.update((list) => [...list]);

    return new Observable((subscriber) => {
      this.api
        .post<any>('admin/ai/execute-tool', {
          tool: msg.proposal!.tool,
          params: msg.proposal!.params,
        })
        .subscribe({
          next: (res) => {
            msg.isExecuting = false;
            msg.executedResult = res;
            this.messages.update((list) => [...list]);
            this.persistMessages();
            subscriber.next(res);
            subscriber.complete();
          },
          error: (err) => {
            msg.isExecuting = false;
            alert(`Error al ejecutar: ${err?.error?.message || err?.message || 'Error desconocido'}`);
            subscriber.error(err);
          },
        });
    });
  }

  private persistMessages() {
    try {
      if (typeof sessionStorage !== 'undefined') {
        sessionStorage.setItem('aura_admin_chat_msgs', JSON.stringify(this.messages()));
      }
    } catch {}
  }

  private restoreSessionMessages() {
    try {
      if (typeof sessionStorage !== 'undefined') {
        const raw = sessionStorage.getItem('aura_admin_chat_msgs');
        if (raw) {
          const parsed = JSON.parse(raw);
          if (Array.isArray(parsed) && parsed.length > 0) {
            this.messages.set(parsed);
            return;
          }
        }
      }
    } catch {}

    this.clearChat();
  }
}
