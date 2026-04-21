import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '@env/environment';
import type { ChatMessage } from '../websocket/ws-event.types';

@Injectable({ providedIn: 'root' })
export class MessageService {
  constructor(private http: HttpClient) {}

  list(conversationId: number, before?: number, limit = 50) {
    const params: Record<string, string> = { limit: String(limit) };
    if (before != null) params['before'] = String(before);
    return this.http.get<ChatMessage[]>(
      `${environment.apiUrl}/conversations/${conversationId}/messages`,
      { params },
    );
  }
}
