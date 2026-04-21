import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '@env/environment';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="auth-wrap">
      <div class="auth-card">
        <h2 class="title">Reset password</h2>

        @if (done()) {
          <div class="success">
            <p>Password updated successfully.</p>
            <a routerLink="/auth/login" class="btn-primary" style="display:block;text-align:center;text-decoration:none;margin-top:16px;">Sign in</a>
          </div>
        } @else {
          <div class="form-group">
            <label class="label">Reset token</label>
            <input class="input" type="text" placeholder="Paste token here" [(ngModel)]="token" />
          </div>
          <div class="form-group">
            <label class="label">New password</label>
            <input class="input" type="password" placeholder="At least 6 characters" [(ngModel)]="password" />
          </div>
          <div class="form-group">
            <label class="label">Confirm new password</label>
            <input class="input" type="password" placeholder="Repeat password" [(ngModel)]="confirm" (keydown.enter)="submit()" />
          </div>
          <button class="btn-primary" (click)="submit()" [disabled]="!canSubmit() || loading()">
            {{ loading() ? 'Saving…' : 'Set new password' }}
          </button>
          @if (error()) { <div class="error">{{ error() }}</div> }
        }

        <div class="links">
          <a routerLink="/auth/login">Back to sign in</a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .auth-wrap { display: flex; justify-content: center; align-items: center; min-height: 100vh; background: #f0f2f5; }
    .auth-card { background: #fff; border-radius: 12px; padding: 36px 32px; width: 100%; max-width: 400px; box-shadow: 0 2px 20px rgba(0,0,0,.1); }
    .title { font-size: 22px; font-weight: 700; margin-bottom: 24px; }
    .form-group { margin-bottom: 16px; }
    .label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 6px; }
    .input { width: 100%; border: 1px solid #ccc; border-radius: 6px; padding: 10px 12px; font-size: 14px; box-sizing: border-box; }
    .input:focus { outline: none; border-color: #5865f2; }
    .btn-primary { width: 100%; padding: 10px; background: #5865f2; color: #fff; border: none; border-radius: 6px; font-size: 15px; font-weight: 600; cursor: pointer; }
    .btn-primary:hover:not(:disabled) { background: #4752c4; }
    .btn-primary:disabled { opacity: 0.6; cursor: default; }
    .error { margin-top: 12px; color: #d32f2f; font-size: 13px; }
    .success { color: #2e7d32; font-size: 14px; }
    .links { margin-top: 20px; text-align: center; font-size: 13px; }
    .links a { color: #5865f2; text-decoration: none; }
    .links a:hover { text-decoration: underline; }
  `],
})
export class ResetPasswordComponent implements OnInit {
  token    = '';
  password = '';
  confirm  = '';
  loading  = signal(false);
  error    = signal('');
  done     = signal(false);

  constructor(private http: HttpClient, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(p => {
      if (p['token']) this.token = p['token'];
    });
  }

  canSubmit(): boolean {
    return !!this.token.trim() && this.password.length >= 6 && this.password === this.confirm;
  }

  submit(): void {
    if (!this.canSubmit()) return;
    this.loading.set(true);
    this.error.set('');
    this.http.post<{ message: string }>(`${environment.apiUrl}/auth/reset-password`, { token: this.token, password: this.password }).subscribe({
      next: () => { this.loading.set(false); this.done.set(true); },
      error: e  => { this.loading.set(false); this.error.set(e.error?.error ?? 'Invalid or expired token.'); },
    });
  }
}
