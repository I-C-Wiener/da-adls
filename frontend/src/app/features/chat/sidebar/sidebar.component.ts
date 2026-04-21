import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { AuthService } from '../../../core/auth/auth.service';
import { MyInvitation, Room, RoomService } from '../../../core/api/room.service';
import { ContactService, FriendEntry } from '../../../core/api/contact.service';
import { WebSocketService } from '../../../core/websocket/websocket.service';
import { CreateRoomDialogComponent } from '../create-room-dialog/create-room-dialog.component';
import { PresenceBadgeComponent } from '../../../shared/components/presence-badge/presence-badge.component';
import { PresenceService } from '../../../core/presence/presence.service';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, FormsModule, MatListModule, MatButtonModule, MatIconModule, MatTooltipModule, MatDialogModule, PresenceBadgeComponent],
  template: `
    <nav class="sidebar">
      <div class="search-box">
        <mat-icon class="search-icon">search</mat-icon>
        <input class="search-input" placeholder="Search…" [(ngModel)]="searchQuery" />
        @if (searchQuery) {
          <button class="clear-btn" (click)="searchQuery = ''">
            <mat-icon>close</mat-icon>
          </button>
        }
      </div>

      <!-- PUBLIC ROOMS accordion -->
      <div class="section-header" (click)="publicOpen.set(!publicOpen())">
        <mat-icon class="chevron" [class.open]="publicOpen()">chevron_right</mat-icon>
        <span>Public Rooms</span>
        <button mat-icon-button class="section-btn" matTooltip="Browse public rooms" (click)="$event.stopPropagation(); browseRooms()">
          <mat-icon>explore</mat-icon>
        </button>
        <button mat-icon-button class="section-btn" matTooltip="Create room" (click)="$event.stopPropagation(); openCreateRoom('public')">
          <mat-icon>add</mat-icon>
        </button>
      </div>
      @if (publicOpen()) {
        <mat-nav-list dense class="room-list">
          @for (room of publicRooms(); track room.id) {
            <a mat-list-item
               [routerLink]="['/chat/room', room.id]"
               routerLinkActive="active-room">
              <span matListItemTitle class="room-item-title">
                # {{ room.name }}
                @if (room.conversationId && unreadCounts().get(room.conversationId)) {
                  <span class="unread-badge">{{ unreadCounts().get(room.conversationId) }}</span>
                }
              </span>
            </a>
          }
          @if (publicRooms().length === 0) {
            <div class="empty-hint">No public rooms joined.</div>
          }
        </mat-nav-list>
      }

      <!-- PRIVATE ROOMS accordion -->
      <div class="section-header" (click)="privateOpen.set(!privateOpen())">
        <mat-icon class="chevron" [class.open]="privateOpen()">chevron_right</mat-icon>
        <span>Private Rooms</span>
        <button mat-icon-button class="section-btn" matTooltip="Create room" (click)="$event.stopPropagation(); openCreateRoom('private')">
          <mat-icon>add</mat-icon>
        </button>
      </div>
      @if (privateOpen()) {
        <mat-nav-list dense class="room-list">
          @for (room of privateRooms(); track room.id) {
            <a mat-list-item
               [routerLink]="['/chat/room', room.id]"
               routerLinkActive="active-room">
              <span matListItemTitle class="room-item-title">
                🔒 {{ room.name }}
                @if (room.conversationId && unreadCounts().get(room.conversationId)) {
                  <span class="unread-badge">{{ unreadCounts().get(room.conversationId) }}</span>
                }
              </span>
            </a>
          }
          @if (privateRooms().length === 0) {
            <div class="empty-hint">No private rooms.</div>
          }
        </mat-nav-list>
      }

      <!-- CONTACTS -->
      <div class="section-header" (click)="contactsOpen.set(!contactsOpen())">
        <mat-icon class="chevron" [class.open]="contactsOpen()">chevron_right</mat-icon>
        <span>Contacts</span>
      </div>
      @if (contactsOpen()) {
        <mat-nav-list dense class="room-list">
          @for (dm of filteredDms(); track dm.userId) {
            <a mat-list-item
               [routerLink]="['/chat/dm', dm.userId]"
               routerLinkActive="active-room">
              <span matListItemTitle class="room-item-title">
                <app-presence-badge [status]="presence.statusOf(dm.userId)" />
                {{ dm.displayName ?? dm.username ?? dm.userId }}
                @if (dm.conversationId && unreadCounts().get(dm.conversationId)) {
                  <span class="unread-badge">{{ unreadCounts().get(dm.conversationId) }}</span>
                }
              </span>
            </a>
          }
          @if (filteredDms().length === 0) {
            <div class="empty-hint">No contacts.</div>
          }
        </mat-nav-list>
      }

      <!-- INVITATIONS -->
      @if (invitations().length > 0) {
        <div class="section-header inv-section">
          <mat-icon class="chevron open">chevron_right</mat-icon>
          <span>Invitations</span>
          <span class="inv-badge">{{ invitations().length }}</span>
        </div>
        <mat-nav-list dense class="room-list">
          @for (inv of invitations(); track inv.id) {
            <div mat-list-item class="inv-row">
              <span class="inv-name"># {{ inv.roomName }}</span>
              <button class="inv-btn" (click)="acceptInvitation(inv)">Join</button>
            </div>
          }
        </mat-nav-list>
      }

      <div class="sidebar-footer">
        <button mat-button class="footer-btn" (click)="openCreateRoom()">
          <mat-icon>add_circle_outline</mat-icon> Create room
        </button>
      </div>
    </nav>
  `,
  styles: [`
    .sidebar { width: 240px; background: #2c2f33; color: #dcddde; display: flex; flex-direction: column; height: 100%; overflow: hidden; flex-shrink: 0; }

    .search-box { display: flex; align-items: center; margin: 10px 10px 6px; background: #23272a; border-radius: 6px; padding: 0 8px; }
    .search-icon { font-size: 18px; color: #72767d; margin-right: 4px; }
    .search-input { flex: 1; background: none; border: none; outline: none; color: #dcddde; font-size: 13px; padding: 7px 0; }
    .search-input::placeholder { color: #72767d; }
    .clear-btn { background: none; border: none; cursor: pointer; color: #72767d; display: flex; align-items: center; padding: 0; }
    .clear-btn mat-icon { font-size: 16px; }

    .section-header {
      display: flex; align-items: center; padding: 8px 8px 8px 4px;
      cursor: pointer; user-select: none; gap: 2px;
      font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: .5px; color: #8e9297;
    }
    .section-header:hover { color: #dcddde; }
    .chevron { font-size: 18px; color: #72767d; transition: transform 0.2s; }
    .chevron.open { transform: rotate(90deg); }
    .section-btn { width: 24px; height: 24px; line-height: 24px; margin-left: auto; color: #72767d; }
    .section-btn mat-icon { font-size: 16px; }

    .room-list { padding: 0 0 4px 0 !important; overflow: visible; }
    .room-list a, .room-list a * { color: #b9bbbe !important; }
    a.active-room, a.active-room * { background: rgba(255,255,255,.08) !important; color: #fff !important; border-radius: 4px; }
    .room-item-title { display: flex; align-items: center; gap: 4px; font-size: 13px; }
    .empty-hint { padding: 4px 16px; font-size: 12px; color: #72767d; }

    .unread-badge { background: #f04747; color: #fff; border-radius: 8px; font-size: 10px; padding: 1px 5px; margin-left: auto; font-weight: 700; }
    .inv-badge { background: #f04747; color: #fff; border-radius: 8px; font-size: 10px; padding: 1px 5px; margin-left: 4px; }
    .inv-section { color: #dcddde; }
    .inv-row { display: flex; align-items: center; padding: 6px 12px; gap: 8px; }
    .inv-name { flex: 1; font-size: 13px; color: #dcddde; }
    .inv-btn { background: #5865f2; color: #fff; border: none; border-radius: 4px; padding: 3px 8px; font-size: 11px; cursor: pointer; }

    .sidebar-footer { margin-top: auto; padding: 8px; border-top: 1px solid #23272a; }
    .footer-btn { width: 100%; color: #b9bbbe; font-size: 13px; justify-content: flex-start; }
  `],
})
export class SidebarComponent implements OnInit, OnDestroy {
  rooms        = signal<Room[]>([]);
  dms          = signal<FriendEntry[]>([]);
  invitations  = signal<MyInvitation[]>([]);
  unreadCounts = signal<Map<number, number>>(new Map());

  publicOpen   = signal(true);
  privateOpen  = signal(true);
  contactsOpen = signal(true);
  searchQuery  = '';

  publicRooms  = computed(() => this.rooms().filter(r => r.visibility === 'public' && this.matchRoom(r.name)));
  privateRooms = computed(() => this.rooms().filter(r => r.visibility === 'private' && this.matchRoom(r.name)));
  filteredDms  = computed(() => this.dms().filter(dm => {
    const q = this.searchQuery.toLowerCase();
    if (!q) return true;
    return (dm.displayName ?? dm.username ?? '').toLowerCase().includes(q);
  }));

  private wsSub?: Subscription;
  private routeSub?: Subscription;

  constructor(
    private auth: AuthService,
    private roomService: RoomService,
    private contactService: ContactService,
    private ws: WebSocketService,
    public presence: PresenceService,
    private dialog: MatDialog,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.loadMyRooms();
    this.loadDms();
    this.loadInvitations();

    this.routeSub = this.router.events.pipe(
      filter(e => e instanceof NavigationEnd),
    ).subscribe((e) => {
      const ne = e as NavigationEnd;
      if (/\/chat\/(room|dm)\//.test(ne.urlAfterRedirects)) {
        this.publicOpen.set(false);
        this.privateOpen.set(false);
        this.contactsOpen.set(false);
      }
    });

    this.wsSub = this.ws.messages().subscribe(event => {
      if (event.type === 'kicked_from_room') {
        this.rooms.update(rooms => rooms.filter(r => r.id !== event.roomId));
        this.router.navigate(['/chat']);
      }
      if (event.type === 'room_invitation') this.loadInvitations();
      if (event.type === 'friend_request_accepted') this.loadDms();
      if (event.type === 'unread_update') {
        this.unreadCounts.update(m => {
          const next = new Map(m);
          if (event.count > 0) next.set(event.conversationId, event.count);
          else next.delete(event.conversationId);
          return next;
        });
      }
    });
  }

  ngOnDestroy(): void { this.wsSub?.unsubscribe(); this.routeSub?.unsubscribe(); }

  private matchRoom(name: string): boolean {
    return !this.searchQuery || name.toLowerCase().includes(this.searchQuery.toLowerCase());
  }

  loadMyRooms(): void {
    this.roomService.myRooms().subscribe({ next: rooms => this.rooms.set(rooms) });
  }

  loadDms(): void {
    this.contactService.list().subscribe({
      next: res => this.dms.set(res.friends.filter(f => f.conversationId != null)),
    });
  }

  openCreateRoom(defaultVisibility: 'public' | 'private' = 'public'): void {
    const ref = this.dialog.open(CreateRoomDialogComponent, { width: '420px', data: { defaultVisibility } });
    ref.afterClosed().subscribe(created => {
      if (created) {
        this.loadMyRooms();
        this.router.navigate(['/chat/room', created.id]);
      }
    });
  }

  loadInvitations(): void {
    this.roomService.myInvitations().subscribe({ next: invs => this.invitations.set(invs), error: () => {} });
  }

  acceptInvitation(inv: MyInvitation): void {
    this.roomService.join(inv.roomId).subscribe({
      next: () => {
        this.invitations.update(list => list.filter(i => i.id !== inv.id));
        this.loadMyRooms();
        this.router.navigate(['/chat/room', inv.roomId]);
      },
      error: (e) => alert(e.error?.error ?? 'Could not join room'),
    });
  }

  browseRooms(): void { this.router.navigate(['/chat/browse']); }
  logout(): void      { this.auth.logout().subscribe(); }
}
