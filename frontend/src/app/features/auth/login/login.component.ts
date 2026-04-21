import { Component } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, RouterLink],
  template: `
    <div class="auth-container">
      <h1>Sign in</h1>
      <form [formGroup]="form" (ngSubmit)="submit()">
        <mat-form-field>
          <mat-label>Email or username</mat-label>
          <input matInput formControlName="login" autocomplete="username">
        </mat-form-field>
        <mat-form-field>
          <mat-label>Password</mat-label>
          <input matInput type="password" formControlName="password" autocomplete="current-password">
        </mat-form-field>
        @if (error) { <p class="error">{{ error }}</p> }
        <button mat-raised-button color="primary" type="submit" [disabled]="form.invalid || loading">
          Sign in
        </button>
        <a routerLink="/auth/forgot-password" class="forgot-link">Forgot password?</a>
        <a routerLink="/auth/register">Create account</a>
      </form>
    </div>
  `,
  styles: [`
    .auth-container { max-width: 400px; margin: 80px auto; padding: 24px; display: flex; flex-direction: column; gap: 16px; }
    form { display: flex; flex-direction: column; gap: 16px; }
    .error { color: red; margin: 0; }
    .forgot-link { font-size: 13px; color: #5865f2; text-decoration: none; text-align: right; }
    .forgot-link:hover { text-decoration: underline; }
  `],
})
export class LoginComponent {
  form = this.fb.nonNullable.group({
    login:    ['', Validators.required],
    password: ['', Validators.required],
  });
  error = '';
  loading = false;

  constructor(private fb: FormBuilder, private auth: AuthService, private router: Router) {}

  submit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    const { login, password } = this.form.getRawValue();
    this.auth.login(login, password).subscribe({
      next: () => this.router.navigate(['/chat']),
      error: () => { this.error = 'Invalid credentials'; this.loading = false; },
    });
  }
}
