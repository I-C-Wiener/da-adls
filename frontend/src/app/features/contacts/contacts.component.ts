import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ContactService, ContactsResponse, FriendEntry, PendingRequest } from '../../core/api/contact.service';
import { WebSocketService } from '../../core/websocket/websocket.service';
import { TopNavComponent } from '../../shared/components/top-nav/top-nav.component';

@Component({
  selector: 'app-contacts',
  standalone: true,
  imports: [FormsModule, TopNavComponent],
  template: `
    <app-top-nav />
    <div class="contacts-page">
      <h2 class="page-title">Contacts</h2>

      <div class="send-request">
        <input
          class="input"
          placeholder="Username to add..."
          [(ngModel)]="addUsername"
        />
        <input
          class="input msg-input"
          placeholder="Optional message..."
          [(ngModel)]="addMessage"
          maxlength="500"
        />
        <button class="btn-primary" (click)="sendRequest()" [disabled]="!addUsername.trim()">
          Send Request
        </button>
        @if (requestMsg()) { <span class="status-msg">{{ requestMsg() }}</span> }
      </div>

      @if (pending().length > 0) {
        <section class="section">
          <h3 class="section-title">Pending Requests</h3>
          @for (req of pending(); track req.friendshipId) {
            <div class="contact-row">
              <div class="req-left">
                <span class="name">{{ req.displayName ?? req.username ?? req.userId }}</span>
                @if (req.message) { <span class="req-message">"{{ req.message }}"</span> }
              </div>
              <span class="badge">{{ req.direction }}</span>
              @if (req.direction === 'received') {
                <button class="btn-sm btn-green" (click)="respond(req, true)">Accept</button>
                <button class="btn-sm btn-red" (click)="respond(req, false)">Reject</button>
              }
            </div>
          }
        </section>
      }

      <section class="section">
        <h3 class="section-title">Friends ({{ friends().length }})</h3>
        @for (f of friends(); track f.friendshipId) {
          <div class="contact-row">
            <span class="name">{{ f.displayName ?? f.username ?? f.userId }}</span>
            <div class="actions">
              @if (f.conversationId) {
                <button class="btn-sm btn-blue" (click)="openDm(f)">DM</button>
              }
              <button class="btn-sm btn-grey" (click)="remove(f)">Remove</button>
              <button class="btn-sm btn-red" (click)="block(f)">Block</button>
            </div>
          </div>
        }
        @if (friends().length === 0) {
          <p class="empty">No friends yet. Send a request!</p>
        }
      </section>
    </div>
  `,
  styles: [`
    .contacts-page { padding: 24px; max-width: 600px; }
    .page-title { font-size: 20px; font-weight: 700; margin-bottom: 20px; }
    .send-request { display: flex; gap: 8px; align-items: center; margin-bottom: 24px; flex-wrap: wrap; }
    .input { border: 1px solid #ccc; border-radius: 6px; padding: 8px 12px; font-size: 14px; width: 160px; }
    .msg-input { width: 220px; }
    .req-left { flex: 1; display: flex; flex-direction: column; gap: 2px; }
    .req-message { font-size: 12px; color: #666; font-style: italic; }
    .status-msg { font-size: 13px; color: #5865f2; }
    .section { margin-bottom: 24px; }
    .section-title { font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: .5px; color: #8e9297; margin-bottom: 8px; }
    .contact-row { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border-radius: 8px; background: #f5f5f5; margin-bottom: 6px; }
    .name { flex: 1; font-size: 14px; font-weight: 500; }
    .badge { font-size: 11px; color: #999; background: #e0e0e0; padding: 2px 6px; border-radius: 4px; }
    .actions { display: flex; gap: 6px; }
    .btn-primary { padding: 8px 14px; background: #5865f2; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; }
    .btn-sm { padding: 4px 10px; border: none; border-radius: 4px; cursor: pointer; font-size: 12px; color: white; }
    .btn-blue { background: #5865f2; } .btn-green { background: #43b581; } .btn-red { background: #f04747; } .btn-grey { background: #747f8d; }
    .empty { color: #999; font-size: 14px; }
  `],
})
export class ContactsComponent implements OnInit, OnDestroy {
  friends = signal<FriendEntry[]>([]);
  pending = signal<PendingRequest[]>([]);
  addUsername = '';
  addMessage  = '';
  requestMsg = signal('');
  private wsSub?: Subscription;

  constructor(private contactService: ContactService, private ws: WebSocketService, private router: Router) {}

  ngOnInit(): void {
    this.load();
    this.wsSub = this.ws.messages().subscribe(event => {
      if (event.type === 'friend_request_accepted' || event.type === 'friend_request') this.load();
    });
  }

  ngOnDestroy(): void { this.wsSub?.unsubscribe(); }

  load(): void {
    this.contactService.list().subscribe({
      next: (res: ContactsResponse) => {
        this.friends.set(res.friends);
        this.pending.set(res.pending);
      },
    });
  }

  sendRequest(): void {
    const name = this.addUsername.trim();
    if (!name) return;
    const msg = this.addMessage.trim() || undefined;
    this.contactService.sendRequestByUsername(name, msg).subscribe({
      next: () => { this.requestMsg.set('Request sent!'); this.addUsername = ''; this.addMessage = ''; setTimeout(() => this.requestMsg.set(''), 3000); },
      error: (e) => this.requestMsg.set(e.error?.error ?? 'Failed'),
    });
  }

  respond(req: PendingRequest, accept: boolean): void {
    this.contactService.respondRequest(req.friendshipId, accept).subscribe({
      next: () => this.load(),
    });
  }

  openDm(f: FriendEntry): void {
    this.router.navigate(['/chat/dm', f.userId]);
  }

  remove(f: FriendEntry): void {
    this.contactService.remove(f.userId).subscribe({ next: () => this.load() });
  }

  block(f: FriendEntry): void {
    this.contactService.block(f.userId).subscribe({ next: () => this.load() });
  }
}
