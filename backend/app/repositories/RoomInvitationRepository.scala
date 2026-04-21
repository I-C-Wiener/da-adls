package repositories

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.PostgresProfile

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class RoomInvitation(
  id: Long,
  roomId: Long,
  invitedBy: Long,
  invitedUserId: Long,
  status: String,
  createdAt: Instant,
)

@Singleton
class RoomInvitationRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[PostgresProfile] {
  import profile.api._

  private class InvitationsTable(tag: Tag) extends Table[RoomInvitation](tag, "room_invitations") {
    def id            = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def roomId        = column[Long]("room_id")
    def invitedBy     = column[Long]("invited_by")
    def invitedUserId = column[Long]("invited_user_id")
    def status        = column[String]("status")
    def createdAt     = column[Instant]("created_at")
    def * = (id, roomId, invitedBy, invitedUserId, status, createdAt).mapTo[RoomInvitation]
  }

  private val invitations = TableQuery[InvitationsTable]

  def create(roomId: Long, invitedBy: Long, invitedUserId: Long): Future[RoomInvitation] =
    db.run(
      (invitations returning invitations.map(_.id) into ((inv, id) => inv.copy(id = id))) +=
        RoomInvitation(0L, roomId, invitedBy, invitedUserId, "pending", Instant.now())
    )

  def findByRoom(roomId: Long): Future[Seq[RoomInvitation]] =
    db.run(invitations.filter(_.roomId === roomId).sortBy(_.createdAt.desc).result)

  def findPendingForUser(userId: Long): Future[Seq[RoomInvitation]] =
    db.run(invitations.filter(i => i.invitedUserId === userId && i.status === "pending").result)

  def findPending(roomId: Long, userId: Long): Future[Option[RoomInvitation]] =
    db.run(invitations.filter(i => i.roomId === roomId && i.invitedUserId === userId && i.status === "pending").result.headOption)

  def accept(id: Long): Future[Unit] =
    db.run(invitations.filter(_.id === id).map(_.status).update("accepted")).map(_ => ())

  def deleteById(id: Long): Future[Unit] =
    db.run(invitations.filter(_.id === id).delete).map(_ => ())
}
