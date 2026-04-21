import { Injectable, OnDestroy } from '@angular/core';
import { Subject, Observable } from 'rxjs';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { environment } from '@env/environment';
import type { WsInbound, WsOutbound } from './ws-event.types';

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private ws: WebSocket | null = null;
  private readonly inbound$ = new Subject<WsInbound>();
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private intentionallyClosed = false;

  constructor(private auth: AuthService, private router: Router) {}

  connect(): void {
    this.intentionallyClosed = false;
    this.openSocket();
  }

  disconnect(): void {
    this.intentionallyClosed = true;
    this.ws?.close();
    this.ws = null;
  }

  send(msg: WsOutbound): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(msg));
    }
  }

  messages(): Observable<WsInbound> {
    return this.inbound$.asObservable();
  }

  private openSocket(): void {
    const wsUrl = `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}${environment.wsUrl}`;
    this.ws = new WebSocket(wsUrl);

    this.ws.onopen = () => {
      const token = this.auth.getToken();
      if (token) this.send({ type: 'auth', token });
    };

    this.ws.onmessage = (event: MessageEvent<string>) => {
      try {
        const msg = JSON.parse(event.data) as WsInbound;
        if (msg.type === 'error' && (msg as any).code === 'SESSION_REVOKED') {
          this.intentionallyClosed = true;
          this.ws?.close();
          localStorage.removeItem('chat_token');
          this.auth.isLoggedIn.set(false);
          this.router.navigate(['/auth/login']);
          return;
        }
        this.inbound$.next(msg);
      } catch {
        console.error('WebSocket parse error', event.data);
      }
    };

    this.ws.onclose = () => {
      if (!this.intentionallyClosed) this.scheduleReconnect();
    };

    this.ws.onerror = () => this.ws?.close();
  }

  private scheduleReconnect(delay = 2000): void {
    this.reconnectTimer = setTimeout(() => this.openSocket(), Math.min(delay * 1.5, 30000));
  }

  ngOnDestroy(): void {
    this.disconnect();
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
  }
}
