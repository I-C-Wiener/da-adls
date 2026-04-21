import { Component, Input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import type { ChatAttachment } from '../../../core/websocket/ws-event.types';

const TOKEN_KEY = 'chat_token';

@Component({
  selector: 'app-attachment-preview',
  standalone: true,
  imports: [DecimalPipe],
  template: `
    <div class="attachment" [class.image]="isImage()">
      @if (isImage()) {
        <a [href]="authedUrl()" target="_blank" rel="noopener noreferrer">
          <img [src]="authedUrl()" [alt]="attachment.originalName" />
        </a>
      } @else {
        <a class="file-link" [href]="authedUrl()" target="_blank" rel="noopener noreferrer">
          📎 {{ attachment.originalName }}
        </a>
      }
      <div class="meta-row">
        <span class="meta">{{ attachment.fileSizeBytes | number }} bytes</span>
      </div>
      @if (attachment.comment) {
        <span class="comment">{{ attachment.comment }}</span>
      }
    </div>
  `,
  styles: [`
    .attachment { display: flex; flex-direction: column; gap: 6px; border: 1px solid #e0e0e0; border-radius: 10px; padding: 8px; max-width: 320px; background: #f8f9fc; }
    .attachment.image img { width: 100%; max-height: 260px; object-fit: contain; border-radius: 10px; }
    .file-link { color: #5865f2; text-decoration: none; font-weight: 600; font-size: 13px; }
    .file-link:hover { text-decoration: underline; }
    .meta-row { display: flex; align-items: center; gap: 8px; }
    .meta { font-size: 11px; color: #999; }
    .comment { font-size: 12px; color: #555; font-style: italic; border-top: 1px solid #eee; padding-top: 4px; margin-top: 2px; }
  `],
})
export class AttachmentPreviewComponent {
  @Input() attachment!: ChatAttachment;

  isImage(): boolean {
    return this.attachment.mimeType?.startsWith('image/') ?? false;
  }

  authedUrl(): string {
    const token = localStorage.getItem(TOKEN_KEY) ?? '';
    const base = this.attachment.downloadUrl;
    return base.includes('?') ? `${base}&token=${token}` : `${base}?token=${token}`;
  }
}
