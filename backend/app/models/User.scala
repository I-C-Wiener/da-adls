package models

import java.time.Instant
import play.api.libs.json.{Json, Writes}

case class User(
  id: Long,
  email: String,
  username: String,
  passwordHash: String,
  displayName: Option[String],
  avatarUrl: Option[String],
  isDeleted: Boolean,
  createdAt: Instant,
)

case class UserSession(
  id: Long,
  userId: Long,
  tokenHash: String,
  userAgent: Option[String],
  ipAddress: Option[String],
  createdAt: Instant,
  lastSeen: Instant,
  expiresAt: Instant,
)

object UserSession {
  implicit val writes: Writes[UserSession] = Json.writes[UserSession]
}

case class UserView(
  id: Long,
  username: String,
  displayName: Option[String],
  avatarUrl: Option[String],
)
