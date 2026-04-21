package repositories

import models.UserSession
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.PostgresProfile

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserSessionRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[PostgresProfile] {
  import profile.api._

  private class UserSessionsTable(tag: Tag) extends Table[UserSession](tag, "user_sessions") {
    def id         = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId     = column[Long]("user_id")
    def sessionId  = column[String]("token_hash") // reusing token_hash as sessionId
    def userAgent  = column[Option[String]]("user_agent")
    def ipAddress  = column[Option[String]]("ip_address")
    def createdAt  = column[Instant]("created_at")
    def lastSeen   = column[Instant]("last_seen")
    def expiresAt  = column[Instant]("expires_at")
    def * = (id, userId, sessionId, userAgent, ipAddress, createdAt, lastSeen, expiresAt).mapTo[UserSession]
  }

  private val sessions = TableQuery[UserSessionsTable]

  def create(session: UserSession): Future[Long] =
    db.run((sessions returning sessions.map(_.id)) += session)

  def findByUserId(userId: Long): Future[Seq[UserSession]] =
    db.run(sessions.filter(_.userId === userId).result)

  def findBySessionId(sessionId: String): Future[Option[UserSession]] =
    db.run(sessions.filter(_.sessionId === sessionId).result.headOption)

  def updateLastSeen(sessionId: String, lastSeen: Instant): Future[Int] =
    db.run(sessions.filter(_.sessionId === sessionId).map(_.lastSeen).update(lastSeen))

  def delete(sessionId: String): Future[Int] =
    db.run(sessions.filter(_.sessionId === sessionId).delete)

  def deleteExpired(): Future[Int] =
    db.run(sessions.filter(_.expiresAt < Instant.now()).delete)

  def deleteAllForUser(userId: Long): Future[Int] =
    db.run(sessions.filter(_.userId === userId).delete)
}