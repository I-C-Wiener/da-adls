import { Component, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { PresenceBadgeComponent } from '../../../shared/components/presence-badge/presence-badge.component';
import { PresenceService } from '../../../core/presence/presence.service';
import { ChatContextService } from '../../../core/chat-context/chat-context.service';
import { ContactService } from '../../../core/api/contact.service';
import { RoomService } from '../../../core/api/room.service';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-members-panel',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatIconModule, MatTooltipModule, PresenceBadgeComponent],
  template: `
    @if (ctx.roomContext(); as room) {
      <div class="members-panel">
        <div class="panel-section room-info">
          <div class="room-title"># {{ room.roomName }}</div>
          @if (room.description) {
            <div class="room-desc">{{ room.description }}</div>
          }
          <div class="room-badge">{{ room.visibility === 'public' ? 'Public room' : 'Private room' }}</div>
        </div>

        <div class="panel-section">
          <div class="section-label">Members ({{ room.members.length }})</div>
          @for (m of room.members; track m.userId) {
            <div class="member-row" (mouseenter)="hoveredId.set(m.userId)" (mouseleave)="hoveredId.set(null)">
              <app-presence-badge [status]="presence.statusOf(m.userId)" />
              <span class="member-name">{{ m.displayName ?? m.username }}</span>
              @if (m.role === 'owner') { <span class="role-badge owner">owner</span> }
              @else if (m.role === 'admin') { <span class="role-badge admin">admin</span> }
              @if (m.userId !== currentUserId() && hoveredId() === m.userId) {
                <button class="add-friend-btn"
                        [matTooltip]="sentIds().has(m.userId) ? 'Request sent' : 'Add friend'"
                        [disabled]="sentIds().has(m.userId)"
                        (click)="addFriend(m.userId)">
                  <mat-icon>{{ sentIds().has(m.userId) ? 'check' : 'person_add' }}</mat-icon>
                </button>
              }
            </div>
          }
        </div>

        <div class="panel-actions">
          <a mat-stroked-button [routerLink]="['/chat/room', room.roomId, 'manage']" class="manage-btn">
            <mat-icon>settings</mat-icon> Manage room
          </a>
          @if (room.ownerId !== currentUserId()) {
            <button mat-stroked-button color="warn" class="manage-btn leave-btn" (click)="leaveRoom(room.roomId)">
              <mat-icon>exit_to_app</mat-icon> Leave room
            </button>
          }
        </div>
      </div>
    } @else {
      @if (ctx.dmContext(); as dm) {
        <div class="members-panel">
          <div class="panel-section">
            <div class="section-label">Presence</div>
            <div class="member-row">
              <app-presence-badge [status]="presence.statusOf(dm.userId)" />
              <span class="member-name">{{ dm.displayName }}</span>
            </div>
          </div>
        </div>
      } @else {
        <div class="members-panel empty-panel">
          <mat-icon class="empty-icon">forum</mat-icon>
          <div>Select a room or conversation</div>
        </div>
      }
    }
  `,
  styles: [`
    .members-panel {
      width: 220px; min-width: 220px; height: 100%; background: #f4f5f7;
      border-left: 1px solid #dde0e4; display: flex; flex-direction: column;
      overflow-y: auto; flex-shrink: 0;
    }
    .panel-section { padding: 16px 14px 8px; border-bottom: 1px solid #dde0e4; }
    .room-title { font-size: 15px; font-weight: 700; color: #2c3e50; }
    .room-desc { font-size: 12px; color: #666; margin-top: 4px; line-height: 1.4; }
    .room-badge { display: inline-block; margin-top: 6px; font-size: 11px; background: #e8eaf6; color: #3949ab; border-radius: 10px; padding: 2px 8px; }
    .section-label { font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: .5px; color: #8e9297; margin-bottom: 8px; }
    .member-row { display: flex; align-items: center; gap: 6px; padding: 5px 0; min-height: 28px; }
    .member-name { font-size: 13px; color: #2c3e50; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .role-badge { font-size: 10px; padding: 1px 5px; border-radius: 6px; flex-shrink: 0; }
    .role-badge.owner { background: #fff3e0; color: #e65100; }
    .role-badge.admin { background: #e8eaf6; color: #3949ab; }
    .add-friend-btn {
      background: none; border: none; cursor: pointer; padding: 2px; border-radius: 4px;
      display: flex; align-items: center; color: #5865f2; flex-shrink: 0;
    }
    .add-friend-btn:hover:not(:disabled) { background: #e8eaf6; }
    .add-friend-btn:disabled { color: #43b581; cursor: default; }
    .add-friend-btn mat-icon { font-size: 16px; height: 16px; width: 16px; }
    .panel-actions { padding: 12px 14px; display: flex; flex-direction: column; gap: 8px; }
    .manage-btn { width: 100%; font-size: 12px; }
    .leave-btn { border-color: #f04747 !important; color: #f04747 !important; }
    .empty-panel { align-items: center; justify-content: center; gap: 12px; color: #aaa; font-size: 13px; }
    .empty-icon { font-size: 36px; height: 36px; width: 36px; color: #ccc; }
  `],
})
export class MembersPanelComponent {
  hoveredId   = signal<number | null>(null);
  sentIds     = signal<Set<number>>(new Set());
  currentUserId: () => number | null;

  constructor(
    public ctx: ChatContextService,
    public presence: PresenceService,
    private contacts: ContactService,
    private roomService: RoomService,
    private router: Router,
    auth: AuthService,
  ) {
    this.currentUserId = auth.userId.bind(auth);
  }

  leaveRoom(roomId: number): void {
    if (!confirm('Leave this room?')) return;
    this.roomService.leave(roomId).subscribe({
      next: () => { this.ctx.clear(); this.router.navigate(['/chat']); },
      error: (e) => alert(e.error?.error ?? 'Could not leave room'),
    });
  }

  addFriend(userId: number): void {
    this.contacts.sendRequest(userId).subscribe({
      next: () => this.sentIds.update(s => new Set([...s, userId])),
      error: () => this.sentIds.update(s => new Set([...s, userId])), // already sent / already friends
    });
  }
}
