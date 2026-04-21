import { Component, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { TopNavComponent } from '../../shared/components/top-nav/top-nav.component';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [FormsModule, TopNavComponent],
  template: `
    <app-top-nav />
    <div class="profile-page">
      <h2 class="page-title">Profile Settings</h2>

      <section class="section">
        <h3 class="section-title">Change Password</h3>
        <div class="form-group">
          <input class="input" type="password" placeholder="Current password" [(ngModel)]="oldPassword" />
        </div>
        <div class="form-group">
          <input class="input" type="password" placeholder="New password" [(ngModel)]="newPassword" />
        </div>
        <div class="form-group">
          <input class="input" type="password" placeholder="Confirm new password" [(ngModel)]="confirmPassword" />
        </div>
        <button class="btn-primary" (click)="changePassword()" [disabled]="!canChangePassword()">
          Change Password
        </button>
        @if (pwMsg()) { <div class="msg" [class.error]="pwError()">{{ pwMsg() }}</div> }
      </section>

      <section class="section danger-zone">
        <h3 class="section-title danger">Danger Zone</h3>
        <p class="warn-text">Deleting your account is permanent and cannot be undone. All rooms you own will be deleted along with their messages and files.</p>
        <button class="btn-danger" (click)="deleteAccount()">Delete My Account</button>
        @if (deleteMsg()) { <div class="msg error">{{ deleteMsg() }}</div> }
      </section>
    </div>
  `,
  styles: [`
    .profile-page { padding: 24px; max-width: 480px; }
    .page-title { font-size: 20px; font-weight: 700; margin-bottom: 24px; }
    .section { background: #fff; border: 1px solid #e0e0e0; border-radius: 10px; padding: 20px; margin-bottom: 20px; }
    .section-title { font-size: 14px; font-weight: 700; text-transform: uppercase; letter-spacing: .5px; color: #5865f2; margin-bottom: 16px; }
    .danger-zone { border-color: #f04747; }
    .danger { color: #f04747; }
    .form-group { margin-bottom: 12px; }
    .input { width: 100%; border: 1px solid #ccc; border-radius: 6px; padding: 8px 12px; font-size: 14px; box-sizing: border-box; }
    .input:focus { outline: none; border-color: #5865f2; }
    .btn-primary { padding: 8px 18px; background: #5865f2; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; }
    .btn-primary:hover:not(:disabled) { background: #4752c4; }
    .btn-primary:disabled { opacity: 0.5; cursor: default; }
    .btn-danger { padding: 8px 18px; background: #f04747; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; }
    .btn-danger:hover { background: #c93a3a; }
    .msg { margin-top: 10px; font-size: 13px; color: #43b581; }
    .msg.error { color: #f04747; }
    .warn-text { font-size: 13px; color: #666; margin-bottom: 14px; line-height: 1.5; }
  `],
})
export class ProfileComponent {
  oldPassword = '';
  newPassword = '';
  confirmPassword = '';
  pwMsg = signal('');
  pwError = signal(false);
  deleteMsg = signal('');

  constructor(private auth: AuthService, private router: Router) {}

  canChangePassword(): boolean {
    return !!this.oldPassword && !!this.newPassword && this.newPassword === this.confirmPassword;
  }

  changePassword(): void {
    this.auth.changePassword(this.oldPassword, this.newPassword).subscribe({
      next: () => {
        this.pwMsg.set('Password changed successfully.');
        this.pwError.set(false);
        this.oldPassword = '';
        this.newPassword = '';
        this.confirmPassword = '';
        setTimeout(() => this.pwMsg.set(''), 4000);
      },
      error: (e) => {
        this.pwMsg.set(e.error?.error ?? 'Failed to change password');
        this.pwError.set(true);
      },
    });
  }

  deleteAccount(): void {
    if (!confirm('Are you sure you want to delete your account? This action cannot be undone.')) return;
    this.auth.deleteAccount().subscribe({
      next: () => {
        localStorage.clear();
        this.router.navigate(['/auth/login']);
      },
      error: (e) => this.deleteMsg.set(e.error?.error ?? 'Failed to delete account'),
    });
  }
}
