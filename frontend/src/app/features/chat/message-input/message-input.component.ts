import { Component, ElementRef, Input, Output, EventEmitter, signal, ViewChild, HostListener } from '@angular/core';
import { SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpEvent, HttpEventType } from '@angular/common/http';
import { AttachmentService } from '../../../core/api/attachment.service';
import { WebSocketService } from '../../../core/websocket/websocket.service';
import type { ReplyPreview } from '../../../core/websocket/ws-event.types';

const EMOJIS = [
  '😀','😂','😍','😎','🥺','😭','😤','🤔','😅','🤣',
  '❤️','🔥','👍','👎','👏','🙌','🎉','✅','❌','⚡',
  '😊','😢','😡','😱','🤯','😴','🤗','😏','🙄','😇',
  '👀','💪','🤝','🫡','🙏','✌️','🤞','👋','🫶','💯',
  '🚀','🎯','💡','🛠️','📌','📎','🗂️','📝','🔍','💬',
  '🐶','🐱','🦄','🐸','🍕','🍔','☕','🍺','🎮','🎵',
];

@Component({
  selector: 'app-message-input',
  standalone: true,
  imports: [FormsModule, SlicePipe],
  template: `
    @if (replyTo) {
      <div class="reply-bar">
        <span class="reply-icon">↩</span>
        <span class="reply-label">Replying to <strong>{{ replyTo.senderName }}</strong>:</span>
        <span class="reply-preview">{{ replyTo.content | slice:0:80 }}</span>
        <button class="reply-cancel" (click)="cancelReply.emit()">×</button>
      </div>
    }
    @if (pendingFile()) {
      <div class="file-bar">
        <span class="file-icon">📎</span>
        <span class="file-name">{{ pendingFile()!.name }}</span>
        <span class="file-size">({{ formatSize(pendingFile()!.size) }})</span>
        <button class="reply-cancel" (click)="clearPendingFile()" title="Remove">×</button>
      </div>
    }
    <div class="message-input">
      <div class="emoji-wrap">
        <button class="emoji-btn" type="button" (click)="togglePicker($event)" [disabled]="sending()" title="Emoji">😊</button>
        @if (pickerOpen()) {
          <div class="emoji-picker">
            @for (e of emojis; track e) {
              <button class="emoji-item" (click)="insertEmoji(e)">{{ e }}</button>
            }
          </div>
        }
      </div>
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
      <button class="attach-btn" type="button" (click)="fileInput.click()" [disabled]="sending()" title="Attach file">📎</button>
      <input #fileInput type="file" hidden (change)="onFileSelected($event)" />
      <button class="send-btn" (click)="send()" [disabled]="(!content.trim() && !pendingFile()) || sending()">Send</button>
    </div>
    @if (uploadProgress() !== null) {
      <div class="upload-progress">Uploading {{ uploadProgress() }}%</div>
    }
    @if (fileError()) {
      <div class="file-error">⚠ {{ fileError() }}</div>
    }
  `,
  styles: [`
    .reply-bar, .file-bar { display: flex; align-items: center; gap: 6px; padding: 5px 16px; background: #f0f2ff; border-top: 1px solid #dde2ff; font-size: 12px; color: #555; }
    .reply-icon { color: #5865f2; font-size: 14px; flex-shrink: 0; }
    .reply-label { flex-shrink: 0; }
    .reply-preview { flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; color: #777; }
    .reply-cancel { background: none; border: none; cursor: pointer; font-size: 18px; color: #999; line-height: 1; padding: 0 4px; }
    .reply-cancel:hover { color: #333; }

    .file-icon { font-size: 14px; flex-shrink: 0; }
    .file-name { font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 260px; }
    .file-size { color: #999; flex-shrink: 0; }

    .message-input { display: flex; gap: 6px; padding: 10px 16px; border-top: 1px solid #e0e0e0; align-items: flex-end; }
    .input { flex: 1; resize: none; border: 1px solid #ccc; border-radius: 6px; padding: 8px 12px; font-size: 14px; font-family: inherit; line-height: 1.4; max-height: 120px; overflow-y: auto; }
    .input:focus { outline: none; border-color: #5865f2; }
    .input:disabled { background: #f5f5f5; }

    .emoji-wrap { position: relative; flex-shrink: 0; }
    .emoji-btn { padding: 8px 10px; background: #f5f5f5; border: 1px solid #ddd; border-radius: 6px; cursor: pointer; font-size: 16px; line-height: 1; }
    .emoji-btn:hover:not(:disabled) { background: #ebebeb; }
    .emoji-btn:disabled { opacity: 0.5; cursor: default; }

    .emoji-picker {
      position: absolute; bottom: calc(100% + 6px); left: 0;
      background: #fff; border: 1px solid #ddd; border-radius: 10px;
      box-shadow: 0 4px 20px rgba(0,0,0,.15); padding: 8px;
      display: grid; grid-template-columns: repeat(10, 1fr);
      gap: 2px; width: 300px; z-index: 200;
    }
    .emoji-item { background: none; border: none; cursor: pointer; font-size: 18px; padding: 4px; border-radius: 6px; line-height: 1; }
    .emoji-item:hover { background: #f0f2ff; }

    .send-btn, .attach-btn { padding: 8px 12px; background: #5865f2; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; flex-shrink: 0; }
    .attach-btn { width: 40px; }
    .send-btn:hover:not(:disabled), .attach-btn:hover:not(:disabled) { background: #4752c4; }
    .send-btn:disabled, .attach-btn:disabled { opacity: 0.5; cursor: default; }
    .upload-progress { padding: 0 16px 8px; font-size: 12px; color: #555; }
    .file-error { padding: 4px 16px 8px; font-size: 12px; color: #d32f2f; background: #fff5f5; border-top: 1px solid #ffcdd2; }
  `],
})
export class MessageInputComponent {
  @Input({ required: true }) conversationId!: number;
  @Input() replyTo: ReplyPreview | null = null;
  @Output() messageSent  = new EventEmitter<void>();
  @Output() cancelReply  = new EventEmitter<void>();

  @ViewChild('textArea') textArea!: ElementRef<HTMLTextAreaElement>;
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  private static readonly MAX_IMAGE_BYTES = 3 * 1024 * 1024;
  private static readonly MAX_FILE_BYTES  = 20 * 1024 * 1024;

  content        = '';
  sending        = signal(false);
  uploadProgress = signal<number | null>(null);
  pickerOpen     = signal(false);
  pendingFile    = signal<File | null>(null);
  fileError      = signal<string | null>(null);
  readonly emojis = EMOJIS;

  constructor(private ws: WebSocketService, private attachmentService: AttachmentService) {}

  @HostListener('document:click')
  onDocumentClick(): void { this.pickerOpen.set(false); }

  togglePicker(event: Event): void {
    event.stopPropagation();
    this.pickerOpen.update(v => !v);
  }

  insertEmoji(emoji: string): void {
    const el = this.textArea.nativeElement;
    const start = el.selectionStart ?? this.content.length;
    const end   = el.selectionEnd   ?? this.content.length;
    this.content = this.content.slice(0, start) + emoji + this.content.slice(end);
    this.pickerOpen.set(false);
    setTimeout(() => {
      el.focus();
      el.setSelectionRange(start + emoji.length, start + emoji.length);
    });
  }

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
    if (file) { event.preventDefault(); this.stageFile(file); }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.stageFile(file);
    input.value = '';
  }

  clearPendingFile(): void {
    this.pendingFile.set(null);
    this.fileError.set(null);
  }

  private stageFile(file: File): void {
    const isImage = file.type.startsWith('image/');
    const limit   = isImage ? MessageInputComponent.MAX_IMAGE_BYTES : MessageInputComponent.MAX_FILE_BYTES;
    const limitMb = isImage ? 3 : 20;
    if (file.size > limit) {
      this.fileError.set(`File too large: ${this.formatSize(file.size)}. ${isImage ? 'Images' : 'Files'} must be ≤ ${limitMb} MB.`);
      this.pendingFile.set(null);
      return;
    }
    this.fileError.set(null);
    this.pendingFile.set(file);
  }

  send(): void {
    if (this.sending()) return;
    const file = this.pendingFile();
    if (file) {
      this.uploadFile(file);
      return;
    }
    const text = this.content.trim();
    if (!text) return;
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

  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  private uploadFile(file: File): void {
    this.sending.set(true);
    this.uploadProgress.set(0);
    this.attachmentService.upload(this.conversationId, file, this.content.trim() || undefined).subscribe({
      next: (event: HttpEvent<unknown>) => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          this.uploadProgress.set(Math.round((event.loaded / event.total) * 100));
        }
        if (event.type === HttpEventType.Response) {
          this.content = '';
          this.pendingFile.set(null);
          this.uploadProgress.set(null);
          this.sending.set(false);
          this.cancelReply.emit();
          this.messageSent.emit();
        }
      },
      error: (err) => {
        this.uploadProgress.set(null);
        this.sending.set(false);
        const msg = err?.error?.message ?? err?.error?.error ?? 'Upload failed. Please try again.';
        this.fileError.set(msg);
      },
    });
  }
}
