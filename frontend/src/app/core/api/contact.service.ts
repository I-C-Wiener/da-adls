import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '@env/environment';

export interface FriendEntry {
  userId: number;
  username: string | null;
  displayName: string | null;
  avatarUrl: string | null;
  friendshipId: number;
  status: string;
  conversationId: number | null;
}

export interface PendingRequest {
  friendshipId: number;
  userId: number;
  username: string | null;
  displayName: string | null;
  direction: 'sent' | 'received';
  status: string;
  message: string | null;
}

export interface ContactsResponse {
  friends: FriendEntry[];
  pending: PendingRequest[];
}

export interface DmConversationResponse {
  conversationId: number;
  userId: number;
  username?: string;
  displayName?: string;
}

@Injectable({ providedIn: 'root' })
export class ContactService {
  constructor(private http: HttpClient) {}

  list() {
    return this.http.get<ContactsResponse>(`${environment.apiUrl}/contacts`);
  }

  sendRequest(userId: number, message?: string) {
    return this.http.post<{ friendshipId: number; status: string }>(
      `${environment.apiUrl}/contacts/requests`,
      { userId, message }
    );
  }

  sendRequestByUsername(username: string, message?: string) {
    return this.http.post<{ friendshipId: number; status: string }>(
      `${environment.apiUrl}/contacts/requests`,
      { username, message }
    );
  }

  respondRequest(friendshipId: number, accept: boolean) {
    return this.http.put<{ friendshipId: number; status: string }>(
      `${environment.apiUrl}/contacts/requests/${friendshipId}`,
      { accept }
    );
  }

  remove(userId: number) {
    return this.http.delete<void>(`${environment.apiUrl}/contacts/${userId}`);
  }

  block(userId: number) {
    return this.http.post<void>(`${environment.apiUrl}/contacts/${userId}/block`, {});
  }

  unblock(userId: number) {
    return this.http.delete<void>(`${environment.apiUrl}/contacts/${userId}/block`);
  }

  getDmConversation(userId: number) {
    return this.http.get<DmConversationResponse>(`${environment.apiUrl}/dm/${userId}`);
  }
}
