import { Component, ElementRef, Input, Output, EventEmitter, signal, ViewChild, OnChanges } from '@angular/core';
import { SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpEvent, HttpEventType } from '@angular/common/http';
import { AttachmentService } from '../../../core/api/attachment.service';
import { WebSocketService } from '../../../core/websocket/websocket.service';
import type { ReplyPreview } from '../../../core/websocket/ws-event.types';

@Component({
  selector: 'app-message-input',
  standalone: true,
  imports: [FormsModule, SlicePipe],
  template: `
    @if (replyTo) {
      <div class="reply-bar">
        <span class="reply-label">Replying to <strong>{{ replyTo.senderName }}</strong>:</span>
        <span class="reply-preview">{{ replyTo.content | slice:0:80 }}</span>
        <button class="reply-cancel" (click)="cancelReply.emit()">×</button>
      </div>
    }
    <div class="message-input">
      <textarea
        #textArea
        class="input"
        placeholder="Send a message..."
        [(ngModel)]="content"
        (keydown)="onKeydown($event)"
        (paste)="onPaste($event)"
        [disabled]="sending()"
        rows="1"
      ></textarea>
      <button class="attach-btn" type="button" (click)="fileInput.click()" [disabled]="sending()">📎</button>
      <input #fileInput type="file" hidden (change)="onFileSelected($event)" />
      <button class="send-btn" (click)="send()" [disabled]="!content.trim() || sending()">Send</button>
    </div>
    @if (uploadProgress() !== null) {
      <div class="upload-progress">Uploading {{ uploadProgress() }}%</div>
    }
  `,
  styles: [`
    .reply-bar { display: flex; align-items: center; gap: 8px; padding: 6px 16px; background: #f0f2ff; border-top: 1px solid #dde2ff; font-size: 13px; color: #555; }
    .reply-label { flex-shrink: 0; }
    .reply-preview { flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; color: #777; }
    .reply-cancel { background: none; border: none; cursor: pointer; font-size: 18px; color: #999; line-height: 1; padding: 0 4px; }
    .reply-cancel:hover { color: #333; }
    .message-input { display: flex; gap: 8px; padding: 12px 16px; border-top: 1px solid #e0e0e0; align-items: flex-end; }
    .input { flex: 1; resize: none; border: 1px solid #ccc; border-radius: 6px; padding: 8px 12px; font-size: 14px; font-family: inherit; line-height: 1.4; max-height: 120px; overflow-y: auto; }
    .input:focus { outline: none; border-color: #5865f2; }
    .input:disabled { background: #f5f5f5; }
    .send-btn, .attach-btn { padding: 8px 12px; background: #5865f2; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; }
    .attach-btn { width: 44px; }
    .send-btn:hover:not(:disabled), .attach-btn:hover:not(:disabled) { background: #4752c4; }
    .send-btn:disabled, .attach-btn:disabled { opacity: 0.5; cursor: default; }
    .upload-progress { padding: 0 16px 8px; font-size: 12px; color: #555; }
  `],
})
export class MessageInputComponent {
  @Input({ required: true }) conversationId!: number;
  @Input() replyTo: ReplyPreview | null = null;
  @Output() messageSent  = new EventEmitter<void>();
  @Output() cancelReply  = new EventEmitter<void>();

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  content = '';
  sending = signal(false);
  uploadProgress = signal<number | null>(null);

  constructor(
    private ws: WebSocketService,
    private attachmentService: AttachmentService,
  ) {}

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  onPaste(event: ClipboardEvent): void {
    const items = Array.from(event.clipboardData?.items ?? []);
    const fileItem = items.find(item => item.kind === 'file');
    if (!fileItem) return;
    const file = fileItem.getAsFile();
    if (file) { event.preventDefault(); this.uploadFile(file); }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.uploadFile(file);
    input.value = '';
  }

  send(): void {
    const text = this.content.trim();
    if (!text || this.sending()) return;
    this.sending.set(true);
    this.ws.send({
      type: 'send_message',
      conversationId: this.conversationId,
      content: text,
      replyToId: this.replyTo?.id,
    });
    this.content = '';
    this.sending.set(false);
    this.cancelReply.emit();
    this.messageSent.emit();
  }

  private uploadFile(file: File): void {
    if (this.sending()) return;
    this.sending.set(true);
    this.uploadProgress.set(0);

    this.attachmentService.upload(this.conversationId, file, this.content.trim() || undefined).subscribe({
      next: (event: HttpEvent<unknown>) => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          this.uploadProgress.set(Math.round((event.loaded / event.total) * 100));
        }
        if (event.type === HttpEventType.Response) {
          this.content = '';
          this.uploadProgress.set(null);
          this.sending.set(false);
          this.cancelReply.emit();
          this.messageSent.emit();
        }
      },
      error: () => { this.uploadProgress.set(null); this.sending.set(false); },
    });
  }
}
