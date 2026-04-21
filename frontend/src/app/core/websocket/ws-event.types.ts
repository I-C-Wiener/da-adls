// Client → Server
export interface WsAuthMessage      { type: 'auth'; token: string }
export interface WsPingMessage      { type: 'ping'; activeTab: boolean }
export interface WsSendMessage      { type: 'send_message'; conversationId: number; content: string; replyToId?: number; ref?: string }
export interface WsEditMessage      { type: 'edit_message'; messageId: number; content: string; ref?: string }
export interface WsDeleteMessage    { type: 'delete_message'; messageId: number; ref?: string }
export interface WsTypingStart      { type: 'typing_start'; conversationId: number }
export interface WsMarkRead         { type: 'mark_read'; conversationId: number; lastMessageId: number }

export type WsOutbound =
  | WsAuthMessage | WsPingMessage | WsSendMessage
  | WsEditMessage | WsDeleteMessage | WsTypingStart | WsMarkRead;

// Server → Client
export interface WsAuthOk           { type: 'auth_ok'; userId: number }
export interface WsError            { type: 'error'; code: string; message: string }
export interface WsAck              { type: 'ack'; ref?: string }
export interface WsMessageReceived  { type: 'message_received'; message: ChatMessage }
export interface WsMessageEdited    { type: 'message_edited'; messageId: number; content: string }
export interface WsMessageDeleted   { type: 'message_deleted'; messageId: number }
export interface WsPresenceUpdate   { type: 'presence_update'; userId: number; status: 'online' | 'afk' | 'offline' }
export interface WsTypingEvent      { type: 'typing'; conversationId: number; userId: number }
export interface WsUnreadUpdate     { type: 'unread_update'; conversationId: number; count: number }
export interface WsFriendRequest          { type: 'friend_request'; from: UserSummary }
export interface WsFriendRequestAccepted  { type: 'friend_request_accepted'; friendId: number; conversationId: number }
export interface WsKickedFromRoom         { type: 'kicked_from_room'; roomId: number }
export interface WsRoomInvitation         { type: 'room_invitation'; roomId: number; invitedBy: string }

export type WsInbound =
  | WsAuthOk | WsError | WsAck | WsMessageReceived | WsMessageEdited
  | WsMessageDeleted | WsPresenceUpdate | WsTypingEvent | WsUnreadUpdate
  | WsFriendRequest | WsFriendRequestAccepted | WsKickedFromRoom | WsRoomInvitation;

// Shared types
export interface ChatAttachment {
  id: number;
  messageId: number;
  originalName: string;
  mimeType: string | null;
  fileSizeBytes: number;
  downloadUrl: string;
  comment?: string | null;
}

export interface ReplyPreview {
  id: number;
  senderName: string;
  content: string | null;
}

export interface ChatMessage {
  id: number;
  conversationId: number;
  senderId: number;
  senderName: string;
  content: string | null;
  replyToId: number | null;
  replyTo?: ReplyPreview | null;
  isEdited: boolean;
  isDeleted: boolean;
  createdAt: string;
  attachments?: ChatAttachment[];
}

export interface UserSummary {
  id: number;
  username: string;
  displayName: string | null;
  avatarUrl: string | null;
}
