import { Component, effect, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  CHATBOT_RESPONSES,
  initialChatMessages,
  initialPublicChatMessages,
} from '../../core/data/mock-data';
import type { ChatMessage } from '../../core/models/pqrs.models';

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
}

@Component({
  selector: 'app-chatbot',
  imports: [FormsModule, RouterLink],
  templateUrl: './chatbot.html',
  styleUrl: './chatbot.css',
})
export class ChatbotPage {
  private readonly route = inject(ActivatedRoute);

  readonly scrollArea = viewChild<ElementRef<HTMLElement>>('scrollArea');

  /** Ruta pública `/asistente` (sin login). */
  readonly isPublicMode = this.route.snapshot.data['publicAssistant'] === true;

  readonly messages = signal<ChatMessage[]>(
    this.isPublicMode ? initialPublicChatMessages() : initialChatMessages(),
  );
  readonly input = signal('');
  readonly isTyping = signal(false);
  constructor() {
    effect(() => {
      this.messages();
      queueMicrotask(() => this.scrollBottom());
    });
  }

  private scrollBottom(): void {
    const el = this.scrollArea()?.nativeElement;
    if (el) el.scrollTop = el.scrollHeight;
  }

  private replyFor(text: string): string {
    const m = text.toLowerCase();
    if (m.includes('peticion') || m.includes('petición') || m.includes('1')) return CHATBOT_RESPONSES['peticion'];
    if (m.includes('queja') || m.includes('2')) return CHATBOT_RESPONSES['queja'];
    if (m.includes('reclamo') || m.includes('3')) return CHATBOT_RESPONSES['reclamo'];
    if (
      m.includes('documento') ||
      m.includes('archivo') ||
      m.includes('pdf') ||
      m.includes('adjunt') ||
      m.includes('subir') ||
      m.includes('cédula') ||
      m.includes('cedula')
    ) {
      return CHATBOT_RESPONSES['documento'];
    }
    if (m.includes('hola') || m.includes('buenos') || m.includes('ayuda')) return CHATBOT_RESPONSES['default'];
    const safe = escapeHtml(text);
    return `Entiendo que necesitas información sobre "${safe}".\n\nIndica si tu trámite es **Petición**, **Queja** o **Reclamo**, o pregunte por **documentos** / **subir archivo**.`;
  }

  private replyForAttachedFile(fileName: string): string {
    const safeName = escapeHtml(fileName);
    const ext = fileName.split('.').pop()?.toLowerCase() ?? '';
    let hint = '';
    if (ext === 'pdf') hint = '\n\nUn **PDF** suele usarse como copia de documentos, facturas o oficios.';
    else if (['jpg', 'jpeg', 'png', 'webp', 'gif'].includes(ext))
      hint = '\n\nLas **imágenes** suelen servir como **evidencia** fotográfica.';
    else if (['doc', 'docx'].includes(ext)) hint = '\n\nLos **Word** a veces se piden convertidos a PDF para radicación; confirme en su caso.';
    return `He tomado nota del archivo **${safeName}**.${hint}\n\n${CHATBOT_RESPONSES['subir']}`;
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.isTyping()) return;
    const summary = `📎 Archivo compartido: ${file.name} (${(file.size / 1024).toFixed(1)} KB)`;
    const userMsg: ChatMessage = {
      id: String(Date.now()),
      role: 'user',
      content: summary,
      timestamp: new Date(),
    };
    this.messages.update((list) => [...list, userMsg]);
    this.isTyping.set(true);
    setTimeout(() => {
      const botMsg: ChatMessage = {
        id: String(Date.now() + 1),
        role: 'assistant',
        content: this.replyForAttachedFile(file.name),
        timestamp: new Date(),
      };
      this.messages.update((list) => [...list, botMsg]);
      this.isTyping.set(false);
    }, 700);
  }

  send(preset?: string): void {
    const text = (preset ?? this.input()).trim();
    if (!text || this.isTyping()) return;
    const userMsg: ChatMessage = {
      id: String(Date.now()),
      role: 'user',
      content: text,
      timestamp: new Date(),
    };
    this.messages.update((list) => [...list, userMsg]);
    this.input.set('');
    this.isTyping.set(true);
    setTimeout(() => {
      const botMsg: ChatMessage = {
        id: String(Date.now() + 1),
        role: 'assistant',
        content: this.replyFor(text),
        timestamp: new Date(),
      };
      this.messages.update((list) => [...list, botMsg]);
      this.isTyping.set(false);
    }, 800);
  }

  formatTime(d: Date): string {
    return d.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' });
  }

  formatContent(raw: string): string {
    return raw
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .split('\n')
      .join('<br/>');
  }
}
