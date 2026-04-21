import { Injectable, signal } from '@angular/core';
import { RoomMember } from '../api/room.service';

export interface RoomContext {
  roomId: number;
  roomName: string;
  description: string | null;
  visibility: 'public' | 'private';
  ownerId: number;
  members: RoomMember[];
}

export interface DmContext {
  userId: number;
  displayName: string;
}

@Injectable({ providedIn: 'root' })
export class ChatContextService {
  readonly roomContext  = signal<RoomContext | null>(null);
  readonly dmContext    = signal<DmContext | null>(null);

  setRoom(ctx: RoomContext): void {
    this.roomContext.set(ctx);
    this.dmContext.set(null);
  }

  setDm(ctx: DmContext): void {
    this.dmContext.set(ctx);
    this.roomContext.set(null);
  }

  clear(): void {
    this.roomContext.set(null);
    this.dmContext.set(null);
  }

  updateMembers(members: RoomMember[]): void {
    const ctx = this.roomContext();
    if (ctx) this.roomContext.set({ ...ctx, members });
  }
}
