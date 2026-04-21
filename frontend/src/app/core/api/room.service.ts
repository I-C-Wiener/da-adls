import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '@env/environment';

export interface Room {
  id: number;
  name: string;
  description: string | null;
  visibility: 'public' | 'private';
  ownerId: number;
  createdAt: string;
  conversationId: number | null;
  memberCount?: number | null;
}

export interface RoomMember {
  userId: number;
  username: string;
  displayName: string | null;
  avatarUrl: string | null;
  role: 'owner' | 'admin' | 'member';
  joinedAt: string;
}

export interface RoomBan {
  userId: number;
  username: string;
  bannedBy: number;
  bannedByUsername: string;
  bannedAt: string;
}

export interface RoomInvitation {
  id: number;
  invitedUserId: number;
  invitedUsername: string;
  invitedBy: number;
  invitedByUsername: string;
  status: string;
  createdAt: string;
}

export interface MyInvitation {
  id: number;
  roomId: number;
  roomName: string;
  status: string;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class RoomService {
  private readonly base = environment.apiUrl;

  constructor(private http: HttpClient) {}

  list(search?: string) {
    const params: Record<string, string> = {};
    if (search) params['search'] = search;
    return this.http.get<Room[]>(`${this.base}/rooms`, { params });
  }

  myRooms() {
    return this.http.get<Room[]>(`${this.base}/rooms/mine`);
  }

  get(id: number) {
    return this.http.get<Room>(`${this.base}/rooms/${id}`);
  }

  create(name: string, description: string, visibility: 'public' | 'private') {
    return this.http.post<Room>(`${this.base}/rooms`, { name, description, visibility });
  }

  update(id: number, name: string, description: string | null, visibility: 'public' | 'private') {
    return this.http.put<{ message: string }>(`${this.base}/rooms/${id}`, { name, description, visibility });
  }

  delete(id: number) {
    return this.http.delete<void>(`${this.base}/rooms/${id}`);
  }

  join(id: number) {
    return this.http.post<{ message: string }>(`${this.base}/rooms/${id}/join`, {});
  }

  leave(id: number) {
    return this.http.post<void>(`${this.base}/rooms/${id}/leave`, {});
  }

  members(id: number) {
    return this.http.get<RoomMember[]>(`${this.base}/rooms/${id}/members`);
  }

  updateRole(roomId: number, userId: number, role: string) {
    return this.http.put<{ message: string }>(`${this.base}/rooms/${roomId}/members/${userId}/role`, { role });
  }

  ban(roomId: number, userId: number) {
    return this.http.post<{ message: string }>(`${this.base}/rooms/${roomId}/members/${userId}/ban`, {});
  }

  getBans(roomId: number) {
    return this.http.get<RoomBan[]>(`${this.base}/rooms/${roomId}/bans`);
  }

  unban(roomId: number, userId: number) {
    return this.http.delete<void>(`${this.base}/rooms/${roomId}/bans/${userId}`);
  }

  getInvitations(roomId: number) {
    return this.http.get<RoomInvitation[]>(`${this.base}/rooms/${roomId}/invitations`);
  }

  invite(roomId: number, username: string) {
    return this.http.post<{ message: string }>(`${this.base}/rooms/${roomId}/invitations`, { username });
  }

  myInvitations() {
    return this.http.get<MyInvitation[]>(`${this.base}/rooms/invitations/mine`);
  }
}
