import { Component, inject, signal, ViewChild, ElementRef, AfterViewChecked, AfterViewInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AuraAgentService, AdminChatMessage } from '../../core/services/aura-agent.service';

@Component({
  selector: 'app-aura-chat-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="aura-chat-container">
      <div class="chat-header">
        <div class="header-left">
          <div class="aura-avatar" aria-hidden="true">A</div>
          <div>
            <h2>Aura <span class="badge badge-primary">Agente Oficial</span></h2>
            <p class="subtitle">Copiloto del Panel de Control con herramientas de administración</p>
          </div>
        </div>

        <div class="header-actions">
          <button
            type="button"
            class="btn btn-secondary btn-sm"
            (click)="clearChat()"
            title="Reiniciar conversación"
            aria-label="Reiniciar conversación con Aura"
          >
            Limpiar Chat
          </button>
        </div>
      </div>

      <!-- SUGERENCIAS RÁPIDAS -->
      <div class="quick-prompts-bar" aria-label="Sugerencias rápidas">
        <button
          type="button"
          class="prompt-chip"
          (click)="sendQuickPrompt('Busca una película destacada en el catálogo y prepárame una notificación push recomendándola.')"
        >
          🎬 Recomendar peli por Push
        </button>
        <button
          type="button"
          class="prompt-chip"
          (click)="sendQuickPrompt('Destaca una obra de ciencia ficción en el banner principal del inicio.')"
        >
          🖼️ Destacar en Banner
        </button>
        <button
          type="button"
          class="prompt-chip"
          (click)="sendQuickPrompt('Crea una nueva ficha para un título exclusivo llamado Los Sonidos del Silencio, año 2026.')"
        >
          ⭐ Crear Ficha Exclusiva
        </button>
        <button
          type="button"
          class="prompt-chip"
          (click)="sendQuickPrompt('¿Cómo están las estadísticas actuales de usuarios y catálogo de la plataforma?')"
        >
          📊 Estado de la plataforma
        </button>
      </div>

      <!-- ANUNCIADORES ACCESIBLES EN VIVO (DOBLE BUFFER ALERT) -->
      <div id="aura-live-announcer-1" class="sr-live-region" role="alert" aria-live="assertive" aria-atomic="true"></div>
      <div id="aura-live-announcer-2" class="sr-live-region" role="alert" aria-live="assertive" aria-atomic="true"></div>

      <!-- LISTA DE MENSAJES (LISTBOX ACCESIBLE) -->
      <div
        class="chat-messages"
        #scrollContainer
        role="listbox"
        aria-label="Mensajes"
        aria-orientation="vertical"
        [attr.aria-activedescendant]="activeMessageId()"
        (keydown)="onListboxKeydown($event)"
      >
        @for (msg of agent.messages(); track msg.id; let i = $index; let isLast = $last) {
          <div
            class="msg-row"
            [class.user-row]="msg.role === 'user'"
            [class.selected-msg]="activeMessageIndex === i"
            role="option"
            [id]="'msg-opt-' + i"
            [attr.aria-selected]="activeMessageIndex === i"
            [attr.tabindex]="(activeMessageIndex === i || (activeMessageIndex === -1 && isLast)) ? 0 : -1"
            [attr.aria-label]="getMessageAriaLabel(msg)"
            (keydown)="onMessageItemKeydown($event, i, msg)"
            (click)="selectMessage(i)"
          >
            @if (msg.role === 'assistant') {
              <div class="aura-bubble-avatar" aria-hidden="true">A</div>
            }

            <div class="msg-bubble" [class.user-bubble]="msg.role === 'user'">
              <div class="msg-text">{{ msg.content }}</div>

              <!-- TARJETA INTERACTIVA DE PROPUESTA DE HERRAMIENTA -->
              @if (msg.proposal) {
                <div class="tool-proposal-card" [class.executed]="msg.executedResult">
                  <div class="proposal-header">
                    <span class="badge" [ngClass]="getBadgeClass(msg.proposal.tool)">
                      {{ getToolBadgeLabel(msg.proposal.tool) }}
                    </span>
                    <strong class="proposal-title">{{ msg.proposal.summary }}</strong>
                  </div>

                  <!-- DETALLES DE LA PROPUESTA SEGÚN LA HERRAMIENTA -->
                  <div class="proposal-details">
                    <!-- NOTIFICACIÓN PUSH -->
                    @if (msg.proposal.tool === 'send_push_notification') {
                      <div class="detail-row">
                        <span class="detail-label">Título:</span>
                        <strong class="detail-val">{{ msg.proposal.params['title'] }}</strong>
                      </div>
                      <div class="detail-row">
                        <span class="detail-label">Mensaje:</span>
                        <span class="detail-val">{{ msg.proposal.params['body'] }}</span>
                      </div>
                      <div class="detail-row">
                        <span class="detail-label">Destino:</span>
                        <span class="badge badge-info">{{ msg.proposal.params['destination'] }}</span>
                        @if (msg.proposal.params['contentId']) {
                          <small style="color: var(--text-muted);">
                            (Obra ID: {{ msg.proposal.params['contentId'] }} - {{ msg.proposal.params['contentType'] || 'catálogo' }})
                          </small>
                        }
                      </div>
                      @if (msg.proposal.params['url']) {
                        <div class="detail-row">
                          <span class="detail-label">Enlace:</span>
                          <small class="detail-val" style="word-break: break-all;">{{ msg.proposal.params['url'] }}</small>
                        </div>
                      }
                    }

                    <!-- BANNER DESTACADO -->
                    @if (msg.proposal.tool === 'publish_banner') {
                      <div class="detail-row">
                        <span class="detail-label">Título:</span>
                        <strong class="detail-val">{{ msg.proposal.params['title'] }}</strong>
                      </div>
                      <div class="detail-row">
                        <span class="detail-label">Mensaje:</span>
                        <span class="detail-val">{{ msg.proposal.params['message'] }}</span>
                      </div>
                      @if (msg.proposal.params['actionUrl']) {
                        <div class="detail-row">
                          <span class="detail-label">Enlace de acción:</span>
                          <small class="detail-val" style="word-break: break-all;">{{ msg.proposal.params['actionUrl'] }}</small>
                        </div>
                      }
                      <div class="detail-row">
                        <span class="detail-label">Enviar Push simultáneo:</span>
                        <span>{{ msg.proposal.params['sendPush'] ? 'Sí' : 'No' }}</span>
                      </div>
                    }

                    <!-- CREAR FICHA EXCLUSIVA -->
                    @if (msg.proposal.tool === 'create_exclusive_content') {
                      <div class="detail-row">
                        <span class="detail-label">Título:</span>
                        <strong class="detail-val">{{ msg.proposal.params['title'] }}</strong>
                      </div>
                      <div class="detail-row">
                        <span class="detail-label">Tipo:</span>
                        <span class="badge badge-info">{{ msg.proposal.params['type'] || 'MOVIE' }}</span>
                        <span class="badge badge-warning">Exclusiva</span>
                      </div>
                      <div class="detail-row">
                        <span class="detail-label">Sinopsis:</span>
                        <small class="detail-val">{{ msg.proposal.params['synopsis'] }}</small>
                      </div>
                      @if (msg.proposal.params['director']) {
                        <div class="detail-row">
                          <span class="detail-label">Director:</span>
                          <span>{{ msg.proposal.params['director'] }}</span>
                        </div>
                      }
                      <div class="detail-row">
                        <span class="detail-label">Estado Inicial:</span>
                        <span class="badge badge-secondary">Borrador (listo para editar y subir archivos)</span>
                      </div>
                    }

                    <!-- COMUNICADO OFICIAL -->
                    @if (msg.proposal.tool === 'publish_announcement') {
                      <div class="detail-row">
                        <span class="detail-label">Título:</span>
                        <strong class="detail-val">{{ msg.proposal.params['title'] }}</strong>
                      </div>
                      <div class="detail-row">
                        <span class="detail-label">Contenido:</span>
                        <span class="detail-val">{{ msg.proposal.params['content'] }}</span>
                      </div>
                    }

                    <!-- MENSAJE AL CHAT GLOBAL -->
                    @if (msg.proposal.tool === 'send_global_chat_message') {
                      <div class="detail-row">
                        <span class="detail-label">Mensaje como &#64;Aura:</span>
                        <strong class="detail-val">"{{ msg.proposal.params['message'] }}"</strong>
                      </div>
                    }
                  </div>

                  <!-- ACCIÓN / CONFIRMACIÓN -->
                  <div class="proposal-actions">
                    @if (!msg.executedResult) {
                      <button
                        type="button"
                        class="btn btn-primary btn-sm cursor-pointer"
                        (click)="executeProposal(msg)"
                        [disabled]="msg.isExecuting"
                      >
                        {{ msg.isExecuting ? 'Ejecutando en el servidor...' : '✓ Confirmar y Publicar Ahora' }}
                      </button>
                      <small style="color: var(--text-muted); align-self: center;">
                        O responde "Sí, publícala" en el chat.
                      </small>
                    } @else {
                      <div class="execution-success">
                        <span class="badge badge-success">✓ Ejecutado con Éxito</span>
                        <span class="exec-msg">{{ msg.executedResult.message }}</span>

                        @if (msg.executedResult.editRoute) {
                          <a
                            [routerLink]="msg.executedResult.editRoute"
                            class="btn btn-secondary btn-sm"
                            style="margin-top: 6px; display: inline-flex; align-items: center; gap: 6px;"
                          >
                            <span>📝 Abrir y Editar Ficha para subir archivos</span>
                          </a>
                        }
                      </div>
                    }
                  </div>
                </div>
              }

              <time class="msg-time">{{ msg.timestamp | date:'shortTime' }}</time>
            </div>
          </div>
        }

        @if (agent.isSending()) {
          <div class="msg-row" aria-hidden="true">
            <div class="aura-bubble-avatar">A</div>
            <div class="msg-bubble typing-bubble">
              <span class="typing-dot"></span>
              <span class="typing-dot"></span>
              <span class="typing-dot"></span>
              <span style="font-size: 12px; color: var(--text-muted); margin-left: 6px;">Aura está pensando y consultando...</span>
            </div>
          </div>
        }
      </div>

      <!-- BARRA DE ENTRADA -->
      <form class="chat-input-bar" (ngSubmit)="sendCurrentMessage()" aria-label="Enviar mensaje a Aura">
        <label for="aura-chat-input" class="sr-only">Escribe tu solicitud para Aura</label>
        <input
          #chatInput
          id="aura-chat-input"
          type="text"
          class="form-control"
          placeholder="Escribe una instrucción (ej: Recomienda una peli y manda un push, destaca un banner, crea una ficha...)"
          [(ngModel)]="inputText"
          name="chatInput"
          (keydown)="onInputKeydown($event)"
          autocomplete="off"
        />
        <button
          type="submit"
          class="btn btn-primary cursor-pointer"
          [disabled]="!inputText.trim() || agent.isSending()"
          aria-label="Enviar mensaje"
        >
          {{ agent.isSending() ? 'Pensando...' : 'Enviar' }}
        </button>
      </form>
    </div>
  `,
  styles: [`
    .aura-chat-container {
      display: flex;
      flex-direction: column;
      height: 100%;
      min-height: 520px;
      background-color: var(--bg-card);
      border-radius: var(--radius-sm);
      border: 1px solid var(--border-color);
      overflow: hidden;
    }

    .chat-header {
      padding: 14px 20px;
      border-bottom: 1px solid var(--border-color);
      display: flex;
      align-items: center;
      justify-content: space-between;
      background-color: var(--bg-sidebar);
    }

    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .aura-avatar, .aura-bubble-avatar {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: linear-gradient(135deg, #6366f1, #a855f7);
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 800;
      font-size: 15px;
      flex-shrink: 0;
    }

    .aura-bubble-avatar {
      width: 30px;
      height: 30px;
      font-size: 13px;
      margin-top: 4px;
    }

    .chat-header h2 {
      font-size: 16px;
      font-weight: 700;
      margin: 0;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .subtitle {
      font-size: 12px;
      color: var(--text-muted);
      margin: 0;
    }

    .quick-prompts-bar {
      display: flex;
      gap: 8px;
      padding: 10px 16px;
      background-color: rgba(0, 0, 0, 0.15);
      border-bottom: 1px solid var(--border-color);
      overflow-x: auto;
      white-space: nowrap;
    }

    .prompt-chip {
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid var(--border-color);
      color: var(--text-muted);
      font-size: 12px;
      padding: 4px 10px;
      border-radius: 16px;
      cursor: pointer;
      transition: all 0.2s;
    }

    .prompt-chip:hover {
      background: rgba(99, 102, 241, 0.15);
      border-color: var(--primary);
      color: var(--text-main);
    }

    .sr-live-region {
      position: absolute;
      top: 0;
      left: -9999px;
      width: 1px;
      height: 1px;
      overflow: hidden;
      white-space: normal;
    }

    .chat-messages {
      flex: 1;
      overflow-y: auto;
      padding: 20px;
      display: flex;
      flex-direction: column;
      gap: 16px;
      min-height: 340px;
      max-height: calc(100vh - 240px);
      outline: none;
    }

    .msg-row {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      max-width: 85%;
      border-radius: var(--radius-sm);
      outline: none;
      transition: background-color 0.15s ease, box-shadow 0.15s ease;
      position: relative;
    }

    .msg-row[role="option"] {
      cursor: pointer;
      padding: 6px 8px;
      margin: -6px -8px;
    }

    .msg-row[role="option"]:focus,
    .msg-row[role="option"]:focus-visible,
    .msg-row.selected-msg:focus-visible {
      outline: 2px solid var(--primary);
      outline-offset: 3px;
      background-color: rgba(99, 102, 241, 0.08);
      border-radius: var(--radius-sm);
    }

    .msg-row.user-row {
      margin-left: auto;
      justify-content: flex-end;
    }

    .msg-bubble {
      background-color: var(--bg-input);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-sm);
      padding: 12px 16px;
      color: var(--text-main);
      font-size: 14px;
      line-height: 1.5;
      display: flex;
      flex-direction: column;
      gap: 8px;
      word-break: break-word;
    }

    .msg-bubble.user-bubble {
      background-color: var(--primary);
      color: #ffffff;
      border-color: var(--primary);
    }

    .msg-time {
      font-size: 10px;
      color: var(--text-dim);
      align-self: flex-end;
    }

    .user-bubble .msg-time {
      color: rgba(255, 255, 255, 0.7);
    }

    .tool-proposal-card {
      background: rgba(99, 102, 241, 0.08);
      border: 1px solid rgba(99, 102, 241, 0.3);
      border-radius: var(--radius-sm);
      padding: 14px;
      display: flex;
      flex-direction: column;
      gap: 10px;
      margin-top: 4px;
    }

    .tool-proposal-card.executed {
      background: rgba(16, 185, 129, 0.08);
      border-color: rgba(16, 185, 129, 0.3);
    }

    .proposal-header {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .proposal-title {
      font-size: 13px;
      color: var(--text-main);
    }

    .proposal-details {
      display: flex;
      flex-direction: column;
      gap: 6px;
      font-size: 13px;
      background: var(--bg-card);
      padding: 10px;
      border-radius: 4px;
      border: 1px solid var(--border-color);
    }

    .detail-row {
      display: flex;
      gap: 8px;
      align-items: baseline;
      flex-wrap: wrap;
    }

    .detail-label {
      color: var(--text-muted);
      font-size: 12px;
      min-width: 60px;
    }

    .detail-val {
      color: var(--text-main);
    }

    .proposal-actions {
      display: flex;
      gap: 12px;
      align-items: center;
      flex-wrap: wrap;
      margin-top: 4px;
    }

    .execution-success {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .exec-msg {
      font-size: 13px;
      color: #34d399;
      font-weight: 600;
    }

    .chat-input-bar {
      padding: 14px 20px;
      border-top: 1px solid var(--border-color);
      display: flex;
      gap: 10px;
      background-color: var(--bg-sidebar);
    }

    .typing-bubble {
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 10px 14px;
    }

    .typing-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background-color: var(--primary);
      animation: pulse 1.2s infinite ease-in-out;
    }

    .typing-dot:nth-child(2) { animation-delay: 0.2s; }
    .typing-dot:nth-child(3) { animation-delay: 0.4s; }

    @keyframes pulse {
      0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
      40% { opacity: 1; transform: scale(1.2); }
    }
  `],
})
export class AuraChatPanelComponent implements AfterViewChecked, AfterViewInit, OnDestroy {
  readonly agent = inject(AuraAgentService);

  @ViewChild('scrollContainer') private scrollContainer?: ElementRef<HTMLDivElement>;
  @ViewChild('chatInput') private chatInput?: ElementRef<HTMLInputElement>;

  inputText = '';
  activeMessageIndex = -1;
  private announcerToggle = false;
  private shouldScroll = false;
  private thinkingInterval: any = null;
  private thinkingStep = 0;
  private readonly thinkingPhrases = [
    'Aura está procesando tu solicitud...',
    'Aura está consultando el catálogo y pensando...',
    'Aura sigue procesando la respuesta...',
    'Aura está preparando los detalles...',
  ];

  ngAfterViewInit() {
    // Foco automático natural en el input al cargar o abrir el panel
    setTimeout(() => this.focusInput(), 150);
  }

  ngAfterViewChecked() {
    if (this.shouldScroll) {
      this.scrollToBottom();
      this.shouldScroll = false;
    }
  }

  ngOnDestroy() {
    this.clearThinkingInterval();
  }

  private startThinkingAnnouncements() {
    this.clearThinkingInterval();
    this.thinkingStep = 0;
    // Anuncio inicial inmediato
    this.announceToScreenReader('Aura está procesando tu solicitud...');

    // Anuncio periódico cada 4.5 segundos (en el rango de 3 a 7 segundos)
    this.thinkingInterval = setInterval(() => {
      this.thinkingStep = (this.thinkingStep + 1) % this.thinkingPhrases.length;
      this.announceToScreenReader(this.thinkingPhrases[this.thinkingStep]);
    }, 4500);
  }

  private clearThinkingInterval() {
    if (this.thinkingInterval) {
      clearInterval(this.thinkingInterval);
      this.thinkingInterval = null;
    }
  }

  announceToScreenReader(message: string) {
    if (!message) return;
    this.announcerToggle = !this.announcerToggle;
    const activeId = this.announcerToggle ? 'aura-live-announcer-1' : 'aura-live-announcer-2';
    const inactiveId = this.announcerToggle ? 'aura-live-announcer-2' : 'aura-live-announcer-1';

    const activeEl = document.getElementById(activeId);
    const inactiveEl = document.getElementById(inactiveId);

    if (inactiveEl) {
      inactiveEl.textContent = '';
    }

    if (activeEl) {
      activeEl.textContent = '';
      setTimeout(() => {
        activeEl.textContent = message;
      }, 70);
    }
  }

  activeMessageId(): string | null {
    if (this.activeMessageIndex >= 0 && this.activeMessageIndex < this.agent.messages().length) {
      return 'msg-opt-' + this.activeMessageIndex;
    }
    return null;
  }

  clearChat() {
    this.agent.clearChat();
    this.activeMessageIndex = -1;
    this.announceToScreenReader('Conversación reiniciada.');
    this.focusInput();
  }

  focusMessageIndex(index: number) {
    const msgs = this.agent.messages();
    if (index < 0 || index >= msgs.length) return;
    this.activeMessageIndex = index;
    const el = document.getElementById('msg-opt-' + index);
    if (el) {
      el.focus();
      el.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }
  }

  selectMessage(index: number) {
    this.focusMessageIndex(index);
  }

  onInputKeydown(event: KeyboardEvent) {
    if (event.ctrlKey && event.key === 'ArrowUp') {
      event.preventDefault();
      const total = this.agent.messages().length;
      if (total > 0) {
        this.focusMessageIndex(total - 1);
      }
    }
  }

  onListboxKeydown(event: KeyboardEvent) {
    if (event.ctrlKey && event.key === 'ArrowDown') {
      event.preventDefault();
      this.focusInput();
      return;
    }

    // Navegación en el contenedor cuando no hay un elemento puntual enfocado
    if (event.target === event.currentTarget) {
      const total = this.agent.messages().length;
      if (total === 0) return;

      if (['ArrowDown', 'ArrowUp', 'Home', 'End', 'PageUp', 'PageDown'].includes(event.key)) {
        event.preventDefault();
        const targetIndex = (this.activeMessageIndex >= 0 && this.activeMessageIndex < total)
          ? this.activeMessageIndex
          : total - 1;
        this.focusMessageIndex(targetIndex);
      }
    }
  }

  onMessageItemKeydown(event: KeyboardEvent, index: number, msg: AdminChatMessage) {
    const total = this.agent.messages().length;
    if (total === 0) return;

    const target = event.target as HTMLElement;
    const isInnerInteractive = target !== event.currentTarget &&
      (target.tagName === 'BUTTON' || target.tagName === 'A' || target.tagName === 'INPUT');

    switch (event.key) {
      case 'ArrowDown':
        if (event.ctrlKey) {
          event.preventDefault();
          this.focusInput();
          break;
        }
        event.preventDefault();
        this.focusMessageIndex(Math.min(total - 1, index + 1));
        break;

      case 'ArrowUp':
        event.preventDefault();
        this.focusMessageIndex(Math.max(0, index - 1));
        break;

      case 'Home':
        event.preventDefault();
        this.focusMessageIndex(0);
        break;

      case 'End':
        event.preventDefault();
        this.focusMessageIndex(total - 1);
        break;

      case 'PageUp':
        event.preventDefault();
        this.focusMessageIndex(Math.max(0, index - 5));
        break;

      case 'PageDown':
        event.preventDefault();
        this.focusMessageIndex(Math.min(total - 1, index + 5));
        break;

      case 'Escape':
        event.preventDefault();
        this.focusInput();
        break;

      case 'Enter':
      case ' ':
        if (!isInnerInteractive && msg.proposal && !msg.executedResult && !msg.isExecuting) {
          event.preventDefault();
          this.executeProposal(msg);
        }
        break;
    }
  }

  getMessageAriaLabel(msg: AdminChatMessage): string {
    const author = msg.role === 'user' ? 'Tú' : 'Aura';
    let label = `${author}: ${msg.content}`;
    if (msg.proposal) {
      label += `. Propuesta: ${msg.proposal.summary}.`;
      if (msg.executedResult) {
        label += ' Estado: Ejecutado con éxito.';
      } else {
        label += ' Estado: Pendiente de confirmación.';
      }
    }
    return label;
  }

  sendCurrentMessage() {
    if (!this.inputText.trim() || this.agent.isSending()) return;
    const text = this.inputText.trim();
    this.inputText = '';
    this.shouldScroll = true;

    // Mantenemos el cursor en el input de inmediato para que nunca salte al documento
    this.focusInput();
    this.startThinkingAnnouncements();

    this.agent.sendMessage(text).subscribe({
      next: (res) => {
        this.clearThinkingInterval();
        this.shouldScroll = true;
        this.activeMessageIndex = this.agent.messages().length - 1;
        const msgText = res?.reply || res?.content || 'He procesado tu solicitud.';
        this.announceToScreenReader(`Aura dice: ${msgText}`);
        this.focusInput();
      },
      error: () => {
        this.clearThinkingInterval();
        this.shouldScroll = true;
        this.announceToScreenReader('Aura dice: Hubo un problema al procesar tu solicitud.');
        this.focusInput();
      },
    });
  }

  sendQuickPrompt(promptText: string) {
    this.inputText = promptText;
    this.sendCurrentMessage();
    this.focusInput();
  }

  executeProposal(msg: AdminChatMessage) {
    this.agent.executeProposal(msg).subscribe({
      next: () => {
        this.shouldScroll = true;
        this.focusInput();
      },
      error: () => {
        this.focusInput();
      },
    });
  }

  getBadgeClass(tool: string): string {
    switch (tool) {
      case 'send_push_notification': return 'badge-danger';
      case 'publish_banner': return 'badge-warning';
      case 'create_exclusive_content': return 'badge-primary';
      case 'publish_announcement': return 'badge-info';
      default: return 'badge-secondary';
    }
  }

  getToolBadgeLabel(tool: string): string {
    switch (tool) {
      case 'send_push_notification': return 'Push FCM';
      case 'publish_banner': return 'Banner';
      case 'create_exclusive_content': return 'Exclusivo';
      case 'publish_announcement': return 'Comunicado';
      case 'send_global_chat_message': return 'Chat Global';
      case 'regenerate_collections': return 'Colecciones';
      case 'recalculate_weekly_ranking': return 'Ranking';
      default: return 'Herramienta';
    }
  }

  private scrollToBottom() {
    if (this.scrollContainer) {
      try {
        this.scrollContainer.nativeElement.scrollTop = this.scrollContainer.nativeElement.scrollHeight;
      } catch {}
    }
  }

  private focusInput() {
    try {
      this.chatInput?.nativeElement?.focus();
    } catch {}
  }
}
