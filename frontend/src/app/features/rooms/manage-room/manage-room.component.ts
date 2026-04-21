import { Component, OnInit, computed, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import { RoomBan, RoomInvitation, RoomMember, RoomService } from '../../../core/api/room.service';

type Tab = 'members' | 'admins' | 'bans' | 'invitations' | 'settings';

@Component({
  selector: 'app-manage-room',
  standalone: true,
  imports: [FormsModule, SlicePipe],
  template: `
    <div class="manage-room">
      <h2 class="title">Manage Room: #{{ roomName() }}</h2>

      <div class="tabs">
        @for (t of tabs; track t.id) {
          <button class="tab" [class.active]="tab() === t.id" (click)="tab.set(t.id)">{{ t.label }}</button>
        }
      </div>

      @if (msg()) { <div class="status-msg">{{ msg() }}</div> }

      <!-- MEMBERS TAB -->
      @if (tab() === 'members') {
        <section class="section">
          <table class="data-table">
            <thead><tr><th>Username</th><th>Role</th><th>Actions</th></tr></thead>
            <tbody>
              @for (m of members(); track m.userId) {
                <tr>
                  <td>{{ m.displayName ?? m.username }}</td>
                  <td><span class="role-badge role-{{ m.role }}">{{ m.role }}</span></td>
                  <td class="actions">
                    @if (m.role !== 'owner') {
                      <button class="btn-sm btn-grey" (click)="toggleAdmin(m)">
                        {{ m.role === 'admin' ? 'Remove Admin' : 'Make Admin' }}
                      </button>
                      <button class="btn-sm btn-orange" (click)="removeMember(m)">Remove</button>
                      <button class="btn-sm btn-red" (click)="banMember(m)">Ban</button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
          @if (members().length === 0) { <p class="empty">No members.</p> }
        </section>
      }

      <!-- ADMINS TAB -->
      @if (tab() === 'admins') {
        <section class="section">
          <p class="hint">Owner is always an admin and cannot lose admin rights.</p>
          <table class="data-table">
            <thead><tr><th>Username</th><th>Role</th><th>Actions</th></tr></thead>
            <tbody>
              @for (m of admins(); track m.userId) {
                <tr>
                  <td>{{ m.displayName ?? m.username }}</td>
                  <td><span class="role-badge role-{{ m.role }}">{{ m.role }}</span></td>
                  <td class="actions">
                    @if (m.role === 'admin') {
                      <button class="btn-sm btn-grey" (click)="toggleAdmin(m)">Remove Admin</button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
          @if (admins().length === 0) { <p class="empty">No admins.</p> }
        </section>
      }

      <!-- BANS TAB -->
      @if (tab() === 'bans') {
        <section class="section">
          <table class="data-table">
            <thead><tr><th>Username</th><th>Banned by</th><th>Date</th><th>Actions</th></tr></thead>
            <tbody>
              @for (b of bans(); track b.userId) {
                <tr>
                  <td>{{ b.username }}</td>
                  <td>{{ b.bannedByUsername }}</td>
                  <td>{{ b.bannedAt | slice:0:10 }}</td>
                  <td><button class="btn-sm btn-grey" (click)="unban(b)">Unban</button></td>
                </tr>
              }
            </tbody>
          </table>
          @if (bans().length === 0) { <p class="empty">No banned users.</p> }
        </section>
      }

      <!-- INVITATIONS TAB -->
      @if (tab() === 'invitations') {
        <section class="section">
          <div class="invite-form">
            <input class="input" [(ngModel)]="inviteUsername" placeholder="Username to invite" />
            <button class="btn-sm btn-primary" (click)="sendInvite()">Send Invite</button>
          </div>
          <table class="data-table" style="margin-top:16px">
            <thead><tr><th>Username</th><th>Invited by</th><th>Status</th><th>Date</th></tr></thead>
            <tbody>
              @for (inv of invitations(); track inv.id) {
                <tr>
                  <td>{{ inv.invitedUsername }}</td>
                  <td>{{ inv.invitedByUsername }}</td>
                  <td><span class="role-badge">{{ inv.status }}</span></td>
                  <td>{{ inv.createdAt | slice:0:10 }}</td>
                </tr>
              }
            </tbody>
          </table>
          @if (invitations().length === 0) { <p class="empty">No invitations sent.</p> }
        </section>
      }

      <!-- SETTINGS TAB -->
      @if (tab() === 'settings') {
        <section class="section settings-form">
          <label>Room name
            <input class="input" [(ngModel)]="settingsName" />
          </label>
          <label>Description
            <textarea class="input" [(ngModel)]="settingsDescription" rows="3"></textarea>
          </label>
          <label>Visibility
            <select class="input" [(ngModel)]="settingsVisibility">
              <option value="public">Public</option>
              <option value="private">Private</option>
            </select>
          </label>
          <div class="settings-actions">
            <button class="btn btn-primary" (click)="saveSettings()">Save changes</button>
            <button class="btn btn-red" (click)="deleteRoom()">Delete room</button>
          </div>
        </section>
      }
    </div>
  `,
  styles: [`
    .manage-room { padding: 24px; max-width: 760px; overflow-y: auto; height: 100%; box-sizing: border-box; }
    .title { font-size: 20px; font-weight: 700; margin-bottom: 16px; }
    .tabs { display: flex; gap: 2px; margin-bottom: 20px; border-bottom: 2px solid #e0e0e0; }
    .tab { padding: 8px 20px; background: none; border: none; cursor: pointer; font-size: 14px; color: #666; border-bottom: 2px solid transparent; margin-bottom: -2px; }
    .tab.active { color: #5865f2; border-bottom-color: #5865f2; font-weight: 600; }
    .section { margin-top: 8px; }
    .hint { font-size: 13px; color: #888; margin-bottom: 12px; }
    .data-table { width: 100%; border-collapse: collapse; font-size: 14px; }
    .data-table th { text-align: left; padding: 8px 10px; background: #f5f5f5; font-size: 12px; color: #666; text-transform: uppercase; }
    .data-table td { padding: 10px 10px; border-bottom: 1px solid #f0f0f0; }
    .actions { display: flex; gap: 6px; }
    .role-badge { font-size: 11px; padding: 2px 7px; border-radius: 10px; background: #e0e0e0; color: #555; }
    .role-badge.role-owner { background: #ffd700; color: #555; }
    .role-badge.role-admin { background: #5865f2; color: #fff; }
    .btn-sm { padding: 4px 10px; border: none; border-radius: 4px; cursor: pointer; font-size: 12px; color: white; white-space: nowrap; }
    .btn-red { background: #f04747; }
    .btn-orange { background: #e67e22; }
    .btn-grey { background: #747f8d; }
    .btn-primary { background: #5865f2; }
    .btn { padding: 8px 18px; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; color: white; }
    .empty { color: #999; font-size: 14px; padding: 12px 0; }
    .status-msg { font-size: 13px; color: #43b581; margin-bottom: 10px; padding: 8px 12px; background: #f0fff4; border-radius: 6px; }
    .invite-form { display: flex; gap: 8px; align-items: center; }
    .input { border: 1px solid #d0d0d0; border-radius: 6px; padding: 8px 10px; font-size: 14px; width: 100%; box-sizing: border-box; }
    textarea.input { resize: vertical; }
    .settings-form { display: flex; flex-direction: column; gap: 16px; max-width: 420px; }
    .settings-form label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; font-weight: 600; color: #555; }
    .settings-actions { display: flex; gap: 12px; margin-top: 8px; }
  `],
})
export class ManageRoomComponent implements OnInit {
  readonly tabs: { id: Tab; label: string }[] = [
    { id: 'members',     label: 'Members' },
    { id: 'admins',      label: 'Admins' },
    { id: 'bans',        label: 'Banned users' },
    { id: 'invitations', label: 'Invitations' },
    { id: 'settings',    label: 'Settings' },
  ];

  tab          = signal<Tab>('members');
  members      = signal<RoomMember[]>([]);
  readonly admins = computed(() => this.members().filter(m => m.role === 'owner' || m.role === 'admin'));
  bans         = signal<RoomBan[]>([]);
  invitations  = signal<RoomInvitation[]>([]);
  msg          = signal('');
  roomName     = signal('');

  inviteUsername   = '';
  settingsName     = '';
  settingsDescription = '';
  settingsVisibility: 'public' | 'private' = 'public';

  private roomId = 0;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private roomService: RoomService,
    private auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.route.params.subscribe(p => {
      this.roomId = Number(p['id']);
      this.load();
    });
  }

  load(): void {
    this.roomService.get(this.roomId).subscribe(room => {
      this.roomName.set(room.name);
      this.settingsName        = room.name;
      this.settingsDescription = room.description ?? '';
      this.settingsVisibility  = room.visibility;
    });
    this.roomService.members(this.roomId).subscribe(ms => this.members.set(ms));
    this.roomService.getBans(this.roomId).subscribe({ next: bs => this.bans.set(bs), error: () => {} });
    this.roomService.getInvitations(this.roomId).subscribe({ next: invs => this.invitations.set(invs), error: () => {} });
  }

  toggleAdmin(m: RoomMember): void {
    const newRole = m.role === 'admin' ? 'member' : 'admin';
    this.roomService.updateRole(this.roomId, m.userId, newRole).subscribe({
      next: () => { this.flash('Role updated'); this.load(); },
      error: (e) => this.flash(e.error?.error ?? 'Failed', true),
    });
  }

  removeMember(m: RoomMember): void {
    if (!confirm(`Remove ${m.username} from this room? They will be banned and cannot rejoin unless unbanned.`)) return;
    this.roomService.ban(this.roomId, m.userId).subscribe({
      next: () => { this.flash(`${m.username} removed`); this.load(); },
      error: (e) => this.flash(e.error?.error ?? 'Failed', true),
    });
  }

  banMember(m: RoomMember): void {
    if (!confirm(`Ban ${m.username} from this room?`)) return;
    this.roomService.ban(this.roomId, m.userId).subscribe({
      next: () => { this.flash(`${m.username} banned`); this.load(); },
      error: (e) => this.flash(e.error?.error ?? 'Failed', true),
    });
  }

  unban(b: RoomBan): void {
    this.roomService.unban(this.roomId, b.userId).subscribe({
      next: () => { this.flash('User unbanned'); this.load(); },
      error: (e) => this.flash(e.error?.error ?? 'Failed', true),
    });
  }

  sendInvite(): void {
    if (!this.inviteUsername.trim()) return;
    this.roomService.invite(this.roomId, this.inviteUsername.trim()).subscribe({
      next: () => { this.flash(`Invited ${this.inviteUsername}`); this.inviteUsername = ''; this.load(); },
      error: (e) => this.flash(e.error?.error ?? 'Failed', true),
    });
  }

  saveSettings(): void {
    if (!this.settingsName.trim()) return;
    this.roomService.update(this.roomId, this.settingsName.trim(), this.settingsDescription || null, this.settingsVisibility).subscribe({
      next: () => { this.flash('Settings saved'); this.load(); },
      error: (e) => this.flash(e.error?.error ?? 'Failed', true),
    });
  }

  deleteRoom(): void {
    if (!confirm('Delete this room permanently? All messages and files will be lost.')) return;
    this.roomService.delete(this.roomId).subscribe({
      next: () => this.router.navigate(['/chat']),
      error: (e) => this.flash(e.error?.error ?? 'Failed', true),
    });
  }

  private flash(text: string, _isError = false): void {
    this.msg.set(text);
    setTimeout(() => this.msg.set(''), 3000);
  }
}
