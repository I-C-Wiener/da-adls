package models

import java.time.Instant

case class Message(
  id: Long,
  conversationId: Long,
  senderId: Long,
  content: Option[String],
  replyToId: Option[Long],
  isEdited: Boolean,
  editedAt: Option[Instant],
  isDeleted: Boolean,
  createdAt: Instant,
  expiresAt: Option[Instant],
)

case class Attachment(
  id: Long,
  messageId: Long,
  originalName: String,
  storedPath: String,
  mimeType: Option[String],
  fileSizeBytes: Long,
  comment: Option[String],
  uploadedAt: Instant,
)

case class Friendship(
  id: Long,
  requesterId: Long,
  addresseeId: Long,
  status: String,
  message: Option[String],
  createdAt: java.time.Instant,
  updatedAt: java.time.Instant,
)

case class Conversation(
  id: Long,
  roomId: Option[Long],
  conversationType: String,
  createdAt: Instant,
)
