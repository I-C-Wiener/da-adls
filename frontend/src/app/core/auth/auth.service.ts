import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { environment } from '@env/environment';

const TOKEN_KEY = 'chat_token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly isLoggedIn = signal(!!localStorage.getItem(TOKEN_KEY));

  constructor(private http: HttpClient, private router: Router) {}

  register(email: string, username: string, password: string) {
    return this.http.post<{ token: string }>(`${environment.apiUrl}/auth/register`, { email, username, password })
      .pipe(tap(res => this.storeToken(res.token)));
  }

  login(login: string, password: string) {
    return this.http.post<{ token: string }>(`${environment.apiUrl}/auth/login`, { login, password })
      .pipe(tap(res => this.storeToken(res.token)));
  }

  logout() {
    return this.http.post(`${environment.apiUrl}/auth/logout`, {}).pipe(
      tap(() => {
        localStorage.removeItem(TOKEN_KEY);
        this.isLoggedIn.set(false);
        this.router.navigate(['/auth/login']);
      }),
    );
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  changePassword(oldPassword: string, newPassword: string) {
    return this.http.put<{ message: string }>(`${environment.apiUrl}/profile/password`, { oldPassword, newPassword });
  }

  deleteAccount() {
    return this.http.delete<void>(`${environment.apiUrl}/profile`);
  }

  userId(): number | null {
    return this.decodeToken()?.userId ?? null;
  }

  username(): string {
    return this.decodeToken()?.username ?? '';
  }

  private decodeToken(): { userId: number; username: string } | null {
    const token = this.getToken();
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return {
        userId: Number(payload.sub),
        username: payload.username ?? '',
      };
    } catch {
      return null;
    }
  }

  clearToken(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.isLoggedIn.set(false);
  }

  private storeToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
    this.isLoggedIn.set(true);
  }
}
