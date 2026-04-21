package models

import java.time.Instant

sealed trait RoomVisibility
object RoomVisibility {
  case object Public  extends RoomVisibility
  case object Private extends RoomVisibility
}

sealed trait MemberRole
object MemberRole {
  case object Owner  extends MemberRole
  case object Admin  extends MemberRole
  case object Member extends MemberRole
}

case class Room(
  id: Long,
  name: String,
  description: Option[String],
  visibility: RoomVisibility,
  ownerId: Long,
  createdAt: Instant,
  deletedAt: Option[Instant],
)

case class RoomMember(
  roomId: Long,
  userId: Long,
  role: MemberRole,
  joinedAt: Instant,
)

case class RoomBan(
  roomId: Long,
  userId: Long,
  bannedBy: Long,
  bannedAt: Instant,
)
