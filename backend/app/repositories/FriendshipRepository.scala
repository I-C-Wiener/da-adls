package repositories

import models.Friendship
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.PostgresProfile

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FriendshipRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[PostgresProfile] {
  import profile.api._

  private class FriendshipsTable(tag: Tag) extends Table[Friendship](tag, "friendships") {
    def id          = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def requesterId = column[Long]("requester_id")
    def addresseeId = column[Long]("addressee_id")
    def status      = column[String]("status")
    def message     = column[Option[String]]("message")
    def createdAt   = column[Instant]("created_at")
    def updatedAt   = column[Instant]("updated_at")
    def * = (id, requesterId, addresseeId, status, message, createdAt, updatedAt).mapTo[Friendship]
  }

  private class UserBansTable(tag: Tag) extends Table[(Long, Long)](tag, "user_bans") {
    def bannerId = column[Long]("banner_id")
    def bannedId = column[Long]("banned_id")
    def * = (bannerId, bannedId)
  }

  private val friendships = TableQuery[FriendshipsTable]
  private val userBans    = TableQuery[UserBansTable]

  def create(requesterId: Long, addresseeId: Long, message: Option[String] = None): Future[Friendship] = {
    val f = Friendship(0L, requesterId, addresseeId, "pending", message, Instant.now(), Instant.now())
    db.run((friendships returning friendships.map(_.id) into ((fr, id) => fr.copy(id = id))) += f)
  }

  def findById(id: Long): Future[Option[Friendship]] =
    db.run(friendships.filter(_.id === id).result.headOption)

  def findBetween(a: Long, b: Long): Future[Option[Friendship]] =
    db.run(
      friendships.filter(f =>
        (f.requesterId === a && f.addresseeId === b) ||
        (f.requesterId === b && f.addresseeId === a)
      ).result.headOption
    )

  def findPendingForUser(userId: Long): Future[Seq[Friendship]] =
    db.run(friendships.filter(f => f.addresseeId === userId && f.status === "pending").result)

  def findAcceptedForUser(userId: Long): Future[Seq[Friendship]] =
    db.run(
      friendships.filter(f =>
        (f.requesterId === userId || f.addresseeId === userId) && f.status === "accepted"
      ).result
    )

  def updateStatus(id: Long, status: String): Future[Unit] =
    db.run(
      friendships.filter(_.id === id)
        .map(f => (f.status, f.updatedAt))
        .update((status, Instant.now()))
    ).map(_ => ())

  def delete(userA: Long, userB: Long): Future[Unit] =
    db.run(
      friendships.filter(f =>
        (f.requesterId === userA && f.addresseeId === userB) ||
        (f.requesterId === userB && f.addresseeId === userA)
      ).delete
    ).map(_ => ())

  def addBan(bannerId: Long, bannedId: Long): Future[Unit] =
    db.run(
      sqlu"""INSERT INTO user_bans (banner_id, banned_id, banned_at)
             VALUES ($bannerId, $bannedId, NOW())
             ON CONFLICT DO NOTHING"""
    ).map(_ => ())

  def removeBan(bannerId: Long, bannedId: Long): Future[Unit] =
    db.run(userBans.filter(b => b.bannerId === bannerId && b.bannedId === bannedId).delete).map(_ => ())

  def isBanned(bannerId: Long, bannedId: Long): Future[Boolean] =
    db.run(userBans.filter(b => b.bannerId === bannerId && b.bannedId === bannedId).exists.result)
}
