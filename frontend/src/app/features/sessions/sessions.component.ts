import { Component, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { TopNavComponent } from '../../shared/components/top-nav/top-nav.component';
import { environment } from '@env/environment';

interface Session {
  sessionId: string;
  userAgent: string | null;
  ipAddress: string | null;
  createdAt: string;
  lastSeen: string;
  expiresAt: string;
  isCurrent: boolean;
}

@Component({
  selector: 'app-sessions',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatIconModule, MatCardModule, TopNavComponent],
  template: `
    <app-top-nav />
    <div class="sessions-page">
      <h2 class="page-title">Active Sessions</h2>
      <p class="page-sub">These are all browsers/devices currently signed in to your account.</p>

      @if (loading()) {
        <div class="loading">Loading sessions…</div>
      } @else {
        <div class="sessions-list">
          @for (s of sessions(); track s.sessionId) {
            <mat-card class="session-card" [class.current-card]="s.isCurrent">
              <div class="session-info">
                <mat-icon class="session-icon">{{ s.isCurrent ? 'computer' : 'devices' }}</mat-icon>
                <div class="session-details">
                  <div class="ua">
                    {{ friendlyBrowser(s.userAgent) }}
                    @if (s.isCurrent) { <span class="current-badge">this device</span> }
                  </div>
                  <div class="meta">
                    IP: {{ friendlyIp(s.ipAddress) }}
                    &nbsp;·&nbsp; Last seen: {{ s.lastSeen | date:'medium' }}
                    &nbsp;·&nbsp; Expires: {{ s.expiresAt | date:'mediumDate' }}
                  </div>
                </div>
              </div>
              <button mat-stroked-button color="warn" (click)="revoke(s.sessionId)" class="revoke-btn">
                <mat-icon>logout</mat-icon> {{ s.isCurrent ? 'Sign out' : 'Revoke' }}
              </button>
            </mat-card>
          }
          @if (sessions().length === 0) {
            <div class="empty">No active sessions found.</div>
          }
        </div>
      }

      @if (error()) {
        <div class="error">{{ error() }}</div>
      }
    </div>
  `,
  styles: [`
    .sessions-page { max-width: 720px; margin: 32px auto; padding: 0 16px; }
    .page-title { font-size: 22px; font-weight: 700; margin-bottom: 4px; }
    .page-sub { color: #666; margin-bottom: 24px; }
    .sessions-list { display: flex; flex-direction: column; gap: 12px; }
    .session-card { padding: 16px 20px; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
    .session-info { display: flex; align-items: flex-start; gap: 14px; flex: 1; min-width: 0; }
    .session-icon { color: #5865f2; margin-top: 2px; flex-shrink: 0; }
    .session-details { min-width: 0; }
    .ua { font-size: 14px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; display: flex; align-items: center; flex-wrap: wrap; gap: 4px; }
    .meta { font-size: 12px; color: #777; margin-top: 4px; }
    .revoke-btn { flex-shrink: 0; }
    .current-card { border-left: 3px solid #5865f2; }
    .current-badge { background: #e8eaf6; color: #3949ab; font-size: 11px; padding: 2px 7px; border-radius: 10px; margin-left: 6px; font-weight: 600; }
    .loading, .empty { color: #777; padding: 24px 0; text-align: center; }
    .error { color: #d32f2f; margin-top: 12px; }
  `],
})
export class SessionsComponent implements OnInit {
  sessions = signal<Session[]>([]);
  loading  = signal(true);
  error    = signal('');

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.http.get<Session[]>(`${environment.apiUrl}/sessions`).subscribe({
      next: s  => { this.sessions.set(s); this.loading.set(false); },
      error: () => { this.error.set('Failed to load sessions.'); this.loading.set(false); },
    });
  }

  friendlyBrowser(ua: string | null): string {
    if (!ua) return 'Unknown browser';
    if (/Edg\//.test(ua))    return this.extractBrowser(ua, 'Edge',    /Edg\/([\d.]+)/);
    if (/OPR\//.test(ua))    return this.extractBrowser(ua, 'Opera',   /OPR\/([\d.]+)/);
    if (/Chrome\//.test(ua)) return this.extractBrowser(ua, 'Chrome',  /Chrome\/([\d.]+)/, this.extractOs(ua));
    if (/Firefox\//.test(ua))return this.extractBrowser(ua, 'Firefox', /Firefox\/([\d.]+)/, this.extractOs(ua));
    if (/Safari\//.test(ua)) return this.extractBrowser(ua, 'Safari',  /Version\/([\d.]+)/, this.extractOs(ua));
    return 'Unknown browser';
  }

  private extractBrowser(ua: string, name: string, versionRe: RegExp, os = ''): string {
    const v = ua.match(versionRe)?.[1]?.split('.')[0] ?? '';
    return [name, v ? `${v}` : '', os].filter(Boolean).join(' ');
  }

  private extractOs(ua: string): string {
    if (/Windows/.test(ua))           return 'on Windows';
    if (/Macintosh|Mac OS X/.test(ua))return 'on macOS';
    if (/Linux/.test(ua))             return 'on Linux';
    if (/Android/.test(ua))           return 'on Android';
    if (/iPhone|iPad/.test(ua))       return 'on iOS';
    return '';
  }

  friendlyIp(ip: string | null): string {
    if (!ip) return 'unknown';
    if (ip === '::1' || ip === '0:0:0:0:0:0:0:1') return '127.0.0.1 (localhost)';
    if (ip === '::ffff:127.0.0.1') return '127.0.0.1 (localhost)';
    return ip;
  }

  revoke(sessionId: string): void {
    this.http.delete(`${environment.apiUrl}/sessions/${sessionId}`).subscribe({
      next: ()  => this.sessions.update(ss => ss.filter(s => s.sessionId !== sessionId)),
      error: () => this.error.set('Failed to revoke session.'),
    });
  }
}
