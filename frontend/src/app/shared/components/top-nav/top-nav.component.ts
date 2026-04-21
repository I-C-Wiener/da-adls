import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatIconModule } from '@angular/material/icon';
import { Subscription } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ContactService } from '../../../core/api/contact.service';
import { WebSocketService } from '../../../core/websocket/websocket.service';

@Component({
  selector: 'app-top-nav',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, MatButtonModule, MatMenuModule, MatIconModule],
  template: `
    <nav class="top-nav">
      <a class="logo" routerLink="/chat">ChatApp</a>
      <div class="nav-links">
        <a class="nav-link" routerLink="/chat/browse" routerLinkActive="active">Public Rooms</a>
        <a class="nav-link" routerLink="/chat" [routerLinkActiveOptions]="{exact: true}" routerLinkActive="active">Private Rooms</a>
        <a class="nav-link" routerLink="/contacts" routerLinkActive="active">
          Contacts
          @if (pendingCount() > 0) {
            <span class="pending-badge">{{ pendingCount() }}</span>
          }
        </a>
        <a class="nav-link" routerLink="/sessions" routerLinkActive="active">Sessions</a>
      </div>
      <div class="nav-right">
        <button mat-button [matMenuTriggerFor]="profileMenu" class="profile-btn">
          <mat-icon>account_circle</mat-icon>
          {{ auth.username() }}
          <mat-icon>arrow_drop_down</mat-icon>
        </button>
        <mat-menu #profileMenu="matMenu">
          <a mat-menu-item routerLink="/profile">
            <mat-icon>manage_accounts</mat-icon> Profile &amp; Settings
          </a>
          <a mat-menu-item routerLink="/sessions">
            <mat-icon>devices</mat-icon> Active Sessions
          </a>
          <button mat-menu-item (click)="logout()">
            <mat-icon>logout</mat-icon> Sign out
          </button>
        </mat-menu>
      </div>
    </nav>
  `,
  styles: [`
    .top-nav {
      display: flex; align-items: center; height: 52px; padding: 0 16px;
      background: #23272a; color: #dcddde; gap: 0; flex-shrink: 0;
      border-bottom: 1px solid #2c2f33; z-index: 100;
    }
    .logo {
      font-size: 18px; font-weight: 700; color: #fff; text-decoration: none;
      padding-right: 24px; letter-spacing: -0.5px; white-space: nowrap;
    }
    .nav-links { display: flex; align-items: center; gap: 0; flex: 1; }
    .nav-link {
      color: #b9bbbe; text-decoration: none; font-size: 14px; font-weight: 500;
      padding: 6px 14px; border-radius: 4px; transition: background 0.15s, color 0.15s;
    }
    .nav-link:hover { color: #fff; background: rgba(255,255,255,.06); }
    .nav-link.active { color: #fff; background: rgba(255,255,255,.1); }
    .nav-right { display: flex; align-items: center; margin-left: auto; }
    .profile-btn { color: #b9bbbe !important; font-size: 13px; display: flex; align-items: center; gap: 2px; }
    .profile-btn mat-icon { font-size: 18px; height: 18px; width: 18px; }
    .pending-badge {
      display: inline-flex; align-items: center; justify-content: center;
      background: #f04747; color: #fff; border-radius: 10px;
      font-size: 10px; font-weight: 700; min-width: 16px; height: 16px;
      padding: 0 4px; margin-left: 5px; vertical-align: middle; line-height: 1;
    }
  `],
})
export class TopNavComponent implements OnInit, OnDestroy {
  pendingCount = signal(0);
  private wsSub?: Subscription;

  constructor(
    public auth: AuthService,
    private router: Router,
    private contacts: ContactService,
    private ws: WebSocketService,
  ) {}

  ngOnInit(): void {
    this.loadPending();
    this.wsSub = this.ws.messages().subscribe(event => {
      if (event.type === 'friend_request' || event.type === 'friend_request_accepted') {
        this.loadPending();
      }
    });
  }

  ngOnDestroy(): void { this.wsSub?.unsubscribe(); }

  private loadPending(): void {
    this.contacts.list().subscribe({
      next: res => this.pendingCount.set(res.pending.filter(p => p.direction === 'received').length),
      error: () => {},
    });
  }

  logout(): void { this.auth.logout().subscribe(); }
}
