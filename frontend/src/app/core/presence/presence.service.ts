import { Injectable, signal, effect } from '@angular/core';
import { WebSocketService } from '../websocket/websocket.service';
import { AuthService } from '../auth/auth.service';
import type { WsPresenceUpdate } from '../websocket/ws-event.types';

@Injectable({ providedIn: 'root' })
export class PresenceService {
  private readonly presenceMap = signal<Map<number, 'online' | 'afk' | 'offline'>>(new Map());

  constructor(ws: WebSocketService, auth: AuthService) {
    ws.messages().subscribe(msg => {
      if (msg.type === 'presence_update') this.handleUpdate(msg);
    });

    effect(() => {
      if (!auth.isLoggedIn()) {
        this.presenceMap.set(new Map());
      }
    });
  }

  statusOf(userId: number): 'online' | 'afk' | 'offline' {
    return this.presenceMap().get(userId) ?? 'offline';
  }

  private handleUpdate(event: WsPresenceUpdate): void {
    const next = new Map(this.presenceMap());
    next.set(event.userId, event.status);
    this.presenceMap.set(next);
  }
}
