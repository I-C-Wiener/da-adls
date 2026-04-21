package models

sealed trait PresenceStatus
object PresenceStatus {
  case object Online  extends PresenceStatus
  case object Afk     extends PresenceStatus
  case object Offline extends PresenceStatus

  def fromString(s: String): PresenceStatus = s match {
    case "online"  => Online
    case "afk"     => Afk
    case _         => Offline
  }

  def toString(s: PresenceStatus): String = s match {
    case Online  => "online"
    case Afk     => "afk"
    case Offline => "offline"
  }
}

case class TabPresence(lastPing: Long, activeTab: Boolean)

case class UserPresence(userId: Long, status: PresenceStatus)
