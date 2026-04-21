import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '@env/environment';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="auth-wrap">
      <div class="auth-card">
        <h2 class="title">Forgot password</h2>
        <p class="sub">Enter your email to receive a reset token.</p>

        @if (!resetToken()) {
          <div class="form-group">
            <label class="label">Email</label>
            <input class="input" type="email" placeholder="you@example.com" [(ngModel)]="email" (keydown.enter)="submit()" />
          </div>
          <button class="btn-primary" (click)="submit()" [disabled]="!email.trim() || loading()">
            {{ loading() ? 'Sending…' : 'Send reset link' }}
          </button>
          @if (error()) { <div class="error">{{ error() }}</div> }
        } @else {
          <div class="token-box">
            <p class="token-label">Copy this token and use it on the reset page:</p>
            <code class="token-value">{{ resetToken() }}</code>
            <a class="reset-link" [routerLink]="['/auth/reset-password']" [queryParams]="{ token: resetToken() }">
              → Go to Reset Password page
            </a>
          </div>
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
    .title { font-size: 22px; font-weight: 700; margin-bottom: 6px; }
    .sub { color: #666; font-size: 14px; margin-bottom: 24px; }
    .form-group { margin-bottom: 16px; }
    .label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 6px; }
    .input { width: 100%; border: 1px solid #ccc; border-radius: 6px; padding: 10px 12px; font-size: 14px; box-sizing: border-box; }
    .input:focus { outline: none; border-color: #5865f2; }
    .btn-primary { width: 100%; padding: 10px; background: #5865f2; color: #fff; border: none; border-radius: 6px; font-size: 15px; font-weight: 600; cursor: pointer; }
    .btn-primary:hover:not(:disabled) { background: #4752c4; }
    .btn-primary:disabled { opacity: 0.6; cursor: default; }
    .error { margin-top: 12px; color: #d32f2f; font-size: 13px; }
    .token-box { background: #f4f5f7; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
    .token-label { font-size: 13px; color: #555; margin-bottom: 10px; }
    .token-value { display: block; font-family: monospace; font-size: 13px; word-break: break-all; background: #e8eaf6; padding: 8px 10px; border-radius: 6px; color: #2c3e50; }
    .reset-link { display: block; margin-top: 14px; color: #5865f2; font-size: 14px; text-decoration: none; font-weight: 600; }
    .reset-link:hover { text-decoration: underline; }
    .links { margin-top: 20px; text-align: center; font-size: 13px; }
    .links a { color: #5865f2; text-decoration: none; }
    .links a:hover { text-decoration: underline; }
  `],
})
export class ForgotPasswordComponent {
  email      = '';
  loading    = signal(false);
  error      = signal('');
  resetToken = signal('');

  constructor(private http: HttpClient) {}

  submit(): void {
    if (!this.email.trim()) return;
    this.loading.set(true);
    this.error.set('');
    this.http.post<{ message: string; resetToken?: string }>(`${environment.apiUrl}/auth/forgot-password`, { email: this.email }).subscribe({
      next: res => {
        this.loading.set(false);
        this.resetToken.set(res.resetToken ?? '');
        if (!res.resetToken) this.error.set('No account found with that email.');
      },
      error: e => {
        this.loading.set(false);
        this.error.set(e.error?.error ?? 'Something went wrong.');
      },
    });
  }
}
