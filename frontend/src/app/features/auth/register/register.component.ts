import { Component } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, RouterLink],
  template: `
    <div class="auth-container">
      <h1>Create account</h1>
      <form [formGroup]="form" (ngSubmit)="submit()">
        <mat-form-field>
          <mat-label>Email</mat-label>
          <input matInput formControlName="email" type="email" autocomplete="email">
        </mat-form-field>
        <mat-form-field>
          <mat-label>Username</mat-label>
          <input matInput formControlName="username" autocomplete="username">
        </mat-form-field>
        <mat-form-field>
          <mat-label>Password</mat-label>
          <input matInput type="password" formControlName="password" autocomplete="new-password">
        </mat-form-field>
        @if (error) { <p class="error">{{ error }}</p> }
        <button mat-raised-button color="primary" type="submit" [disabled]="form.invalid || loading">
          Register
        </button>
        <a routerLink="/auth/login">Already have an account?</a>
      </form>
    </div>
  `,
  styles: [`
    .auth-container { max-width: 400px; margin: 80px auto; padding: 24px; display: flex; flex-direction: column; gap: 16px; }
    form { display: flex; flex-direction: column; gap: 16px; }
    .error { color: red; margin: 0; }
  `],
})
export class RegisterComponent {
  form = this.fb.nonNullable.group({
    email:    ['', [Validators.required, Validators.email]],
    username: ['', [Validators.required, Validators.minLength(3)]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });
  error = '';
  loading = false;

  constructor(private fb: FormBuilder, private auth: AuthService, private router: Router) {}

  submit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    const { email, username, password } = this.form.getRawValue();
    this.auth.register(email, username, password).subscribe({
      next: () => this.router.navigate(['/chat']),
      error: (err) => {
        this.error = err.status === 409 ? 'Email or username already taken' : 'Registration failed';
        this.loading = false;
      },
    });
  }
}
