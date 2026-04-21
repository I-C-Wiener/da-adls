import {
  Component, OnInit, OnDestroy, signal, ViewChild, ElementRef,
  AfterViewInit, AfterViewChecked,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { DatePipe, SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WebSocketService } from '../../../core/websocket/websocket.service';
import { MessageService } from '../../../core/api/message.service';
import { RoomMember, RoomService } from '../../../core/api/room.service';
import { ContactService } from '../../../core/api/contact.service';
import { AuthService } from '../../../core/auth/auth.service';
import { ChatContextService } from '../../../core/chat-context/chat-context.service';
import { AttachmentPreviewComponent } from '../../../shared/components/attachment-preview/attachment-preview.component';
import type { ChatMessage, ReplyPreview, WsMessageReceived, WsMessageEdited, WsMessageDeleted } from '../../../core/websocket/ws-event.types';
import { MessageInputComponent } from '../message-input/message-input.component';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';

@Component({
  selector: 'app-message-area',
  standalone: true,
  imports: [MessageInputComponent, RelativeTimePipe, DatePipe, SlicePipe, FormsModule, AttachmentPreviewComponent],
  template: `
    <div class="message-area">
      <div class="room-header">
        <span class="room-name">{{ roomPrefix() }}{{ roomName() }}</span>
      </div>

      <div class="messages-list" #messageList>
        <div #topSentinel class="sentinel"></div>
        @if (loadingMore()) { <div class="loading">Loading...</div> }
        @for (msg of messages(); track msg.id) {
          <div class="message" [class.deleted]="msg.isDeleted" (mouseenter)="hoveredId.set(msg.id)" (mouseleave)="hoveredId.set(null)">

            @if (msg.replyTo) {
              <div class="reply-preview">
                <span class="reply-sender">{{ msg.replyTo.senderName }}</span>
                <span class="reply-content">{{ msg.replyTo.content ?? '(deleted)' | slice:0:100 }}</span>
              </div>
            }

            <div class="message-meta">
              <span class="sender">{{ msg.senderName }}</span>
              <span class="time" [title]="msg.createdAt | date:'medium'">{{ msg.createdAt | relativeTime }}</span>
              @if (hoveredId() === msg.id && !msg.isDeleted) {
                <div class="msg-actions">
                  <button class="action-btn" title="Reply" (click)="startReply(msg)">↩</button>
                  @if (msg.senderId === currentUserId()) {
                    <button class="action-btn" title="Edit" (click)="startEdit(msg)">✏️</button>
                  }
                  @if (msg.senderId === currentUserId() || isAdminOrOwner()) {
                    <button class="action-btn action-del" title="Delete" (click)="deleteMsg(msg)">🗑</button>
                  }
                </div>
              }
            </div>

            @if (editingId() === msg.id) {
              <div class="edit-row">
                <textarea class="edit-input" [(ngModel)]="editContent" (keydown)="onEditKeydown($event, msg)" rows="2"></textarea>
                <button class="edit-save" (click)="saveEdit(msg)">Save</button>
                <button class="edit-cancel" (click)="cancelEdit()">Cancel</button>
              </div>
            } @else {
              <div class="content">
                {{ msg.isDeleted ? '(deleted)' : msg.content }}
                @if (msg.isEdited && !msg.isDeleted) { <span class="edited">(edited)</span> }
              </div>
            }

            @if (msg.attachments?.length) {
              <div class="attachments">
                @for (attachment of msg.attachments; track attachment.id) {
                  <app-attachment-preview [attachment]="attachment" />
                }
              </div>
            }
          </div>
        }
        @if (messages().length === 0 && !loadingMore()) {
          <div class="empty">No messages yet. Say something!</div>
        }
      </div>

      @if (conversationId()) {
        <app-message-input
          [conversationId]="conversationId()!"
          [replyTo]="replyTo()"
          (messageSent)="scrollToBottom()"
          (cancelReply)="replyTo.set(null)" />
      }
    </div>
  `,
  styles: [`
    .message-area { display: flex; flex-direction: column; height: 100%; overflow: hidden; }
    .room-header { padding: 0 16px; height: 48px; display: flex; align-items: center; gap: 12px; border-bottom: 1px solid #e0e0e0; flex-shrink: 0; }
    .room-name { font-weight: 600; font-size: 16px; }
    .messages-list { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 4px; }
    .sentinel { height: 1px; }
    .message { display: flex; flex-direction: column; gap: 2px; padding: 4px 6px; border-radius: 4px; position: relative; }
    .message:hover { background: #f8f9ff; }
    .message.deleted .content { color: #999; font-style: italic; }
    .message-meta { display: flex; align-items: baseline; gap: 8px; position: relative; }
    .sender { font-weight: 600; font-size: 13px; color: #5865f2; }
    .time { font-size: 11px; color: #999; }
    .msg-actions { display: flex; gap: 2px; margin-left: auto; }
    .action-btn { background: #fff; border: 1px solid #ddd; border-radius: 4px; cursor: pointer; padding: 2px 6px; font-size: 13px; }
    .action-btn:hover { background: #f0f0f0; }
    .action-del:hover { background: #fff0f0; }
    .content { font-size: 14px; line-height: 1.4; word-break: break-word; white-space: pre-wrap; }
    .attachments { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 6px; }
    .edited { font-size: 11px; color: #999; margin-left: 4px; }
    .reply-preview { background: #f0f2ff; border-left: 3px solid #5865f2; padding: 4px 8px; border-radius: 0 4px 4px 0; margin-bottom: 2px; display: flex; gap: 8px; font-size: 12px; max-width: 480px; }
    .reply-sender { font-weight: 600; color: #5865f2; flex-shrink: 0; }
    .reply-content { color: #666; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .edit-row { display: flex; align-items: flex-start; gap: 6px; margin-top: 4px; }
    .edit-input { flex: 1; border: 1px solid #5865f2; border-radius: 6px; padding: 6px 10px; font-size: 14px; font-family: inherit; resize: none; }
    .edit-save, .edit-cancel { padding: 4px 10px; border: none; border-radius: 4px; cursor: pointer; font-size: 12px; }
    .edit-save { background: #5865f2; color: white; }
    .edit-cancel { background: #ccc; color: #333; }
    .loading { text-align: center; color: #999; padding: 8px; }
    .empty { text-align: center; color: #999; padding: 16px; }
  `],
})
export class MessageAreaComponent implements OnInit, OnDestroy, AfterViewInit, AfterViewChecked {
  @ViewChild('messageList') private messageList!: ElementRef<HTMLDivElement>;
  @ViewChild('topSentinel') private topSentinel!: ElementRef<HTMLDivElement>;

  messages       = signal<ChatMessage[]>([]);
  roomName       = signal('');
  roomPrefix     = signal('');
  roomId         = signal<number | null>(null);
  conversationId = signal<number | null>(null);
  members        = signal<RoomMember[]>([]);
  dmUserId       = signal<number | null>(null);
  loadingMore    = signal(false);
  hasMore        = signal(false);
  hoveredId      = signal<number | null>(null);
  editingId      = signal<number | null>(null);
  replyTo        = signal<ReplyPreview | null>(null);
  currentUserId  = signal<number | null>(null);

  editContent = '';
  private shouldScroll = true;
  private observer?: IntersectionObserver;
  private wsSub?: Subscription;
  private routeSub?: Subscription;

  constructor(
    private route: ActivatedRoute,
    private ws: WebSocketService,
    private msgService: MessageService,
    private roomService: RoomService,
    private contactService: ContactService,
    private auth: AuthService,
    private chatCtx: ChatContextService,
  ) {}

  ngOnInit(): void {
    this.currentUserId.set(this.auth.userId());

    this.routeSub = this.route.params.subscribe(params => {
      this.messages.set([]);
      this.members.set([]);
      this.dmUserId.set(null);
      this.hasMore.set(false);
      this.roomName.set('');
      this.roomPrefix.set('');
      this.conversationId.set(null);
      this.shouldScroll = true;
      this.replyTo.set(null);
      this.cancelEdit();
      this.roomId.set(null);
      this.chatCtx.clear();

      if (params['id']) {
        this.roomId.set(Number(params['id']));
        this.loadRoom(Number(params['id']));
      } else if (params['userId']) {
        this.loadDm(Number(params['userId']));
      }
    });

    this.wsSub = this.ws.messages().subscribe(event => {
      if (event.type === 'message_received') this.onMessageReceived(event);
      if (event.type === 'message_edited')   this.onMessageEdited(event);
      if (event.type === 'message_deleted')  this.onMessageDeleted(event);
    });
  }

  ngAfterViewInit(): void {
    this.setupIntersectionObserver();
  }

  private setupIntersectionObserver(): void {
    this.observer?.disconnect();
    this.observer = new IntersectionObserver(entries => {
      if (entries[0].isIntersecting && this.hasMore() && !this.loadingMore()) {
        this.loadMore();
      }
    }, { root: this.messageList?.nativeElement, threshold: 0.1 });

    if (this.topSentinel) {
      this.observer.observe(this.topSentinel.nativeElement);
    }
  }

  loadRoom(roomId: number): void {
    this.roomService.get(roomId).subscribe(room => {
      this.roomName.set(room.name);
      this.roomPrefix.set('# ');
      this.roomService.members(roomId).subscribe(ms => {
        this.members.set(ms);
        this.chatCtx.setRoom({
          roomId:      room.id,
          roomName:    room.name,
          description: room.description,
          visibility:  room.visibility,
          ownerId:     room.ownerId,
          members:     ms,
        });
      });
      if (room.conversationId) {
        this.conversationId.set(room.conversationId);
        this.loadHistory(room.conversationId);
      }
    });
  }

  loadDm(userId: number): void {
    this.roomName.set('Direct message');
    this.dmUserId.set(userId);
    this.contactService.getDmConversation(userId).subscribe({
      next: dm => {
        const label = dm.displayName ?? dm.username ?? `DM`;
        this.roomName.set(label);
        this.roomPrefix.set('@');
        this.chatCtx.setDm({ userId, displayName: label });
        this.conversationId.set(dm.conversationId);
        this.loadHistory(dm.conversationId);
      },
      error: () => this.roomName.set('Direct message'),
    });
  }

  loadHistory(convId: number, before?: number): void {
    this.loadingMore.set(true);
    this.msgService.list(convId, before).subscribe({
      next: msgs => {
        const reversed = [...msgs].reverse();
        if (before) {
          const prevHeight = this.messageList?.nativeElement.scrollHeight ?? 0;
          this.messages.set([...reversed, ...this.messages()]);
          requestAnimationFrame(() => {
            const el = this.messageList?.nativeElement;
            if (el) el.scrollTop = el.scrollHeight - prevHeight;
          });
        } else {
          this.messages.set(reversed);
          this.shouldScroll = true;
        }
        this.hasMore.set(msgs.length === 50);
        this.loadingMore.set(false);
        if (!before) this.markRead();
      },
      error: () => this.loadingMore.set(false),
    });
  }

  private markRead(): void {
    const convId = this.conversationId();
    const msgs = this.messages();
    if (!convId || msgs.length === 0) return;
    const lastId = msgs[msgs.length - 1].id;
    this.ws.send({ type: 'mark_read', conversationId: convId, lastMessageId: lastId });
  }

  loadMore(): void {
    const convId  = this.conversationId();
    const oldest  = this.messages()[0];
    if (convId && oldest) this.loadHistory(convId, oldest.id);
  }

  scrollToBottom(): void { this.shouldScroll = true; }

  ngAfterViewChecked(): void {
    if (this.shouldScroll && this.messageList) {
      const el = this.messageList.nativeElement;
      el.scrollTop = el.scrollHeight;
      this.shouldScroll = false;
    }
  }

  startReply(msg: ChatMessage): void {
    this.replyTo.set({ id: msg.id, senderName: msg.senderName, content: msg.content });
  }

  startEdit(msg: ChatMessage): void {
    this.editingId.set(msg.id);
    this.editContent = msg.content ?? '';
  }

  cancelEdit(): void {
    this.editingId.set(null);
    this.editContent = '';
  }

  onEditKeydown(event: KeyboardEvent, msg: ChatMessage): void {
    if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); this.saveEdit(msg); }
    if (event.key === 'Escape') this.cancelEdit();
  }

  saveEdit(msg: ChatMessage): void {
    const text = this.editContent.trim();
    if (!text) return;
    this.ws.send({ type: 'edit_message', messageId: msg.id, content: text });
    this.cancelEdit();
  }

  deleteMsg(msg: ChatMessage): void {
    if (!confirm('Delete this message?')) return;
    this.ws.send({ type: 'delete_message', messageId: msg.id });
  }

  isAdminOrOwner(): boolean {
    const me = this.currentUserId();
    if (!me) return false;
    const member = this.members().find(m => m.userId === me);
    return member?.role === 'owner' || member?.role === 'admin';
  }

  private onMessageReceived(event: WsMessageReceived): void {
    const convId = this.conversationId();
    if (event.message.conversationId === convId) {
      this.messages.update(msgs => [...msgs, event.message]);
      this.shouldScroll = true;
      this.ws.send({ type: 'mark_read', conversationId: convId, lastMessageId: event.message.id });
    }
  }

  private onMessageEdited(event: WsMessageEdited): void {
    this.messages.update(msgs =>
      msgs.map(m => m.id === event.messageId ? { ...m, content: event.content, isEdited: true } : m)
    );
  }

  private onMessageDeleted(event: WsMessageDeleted): void {
    this.messages.update(msgs =>
      msgs.map(m => m.id === event.messageId ? { ...m, isDeleted: true, content: null } : m)
    );
  }

  ngOnDestroy(): void {
    this.wsSub?.unsubscribe();
    this.routeSub?.unsubscribe();
    this.observer?.disconnect();
    this.chatCtx.clear();
  }
}
