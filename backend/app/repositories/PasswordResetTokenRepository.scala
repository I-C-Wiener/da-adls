package repositories

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.PostgresProfile

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PasswordResetTokenRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[PostgresProfile] {
  import profile.api._

  private class TokensTable(tag: Tag) extends Table[(Long, Long, String, Instant, Boolean)](tag, "password_reset_tokens") {
    def id        = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId    = column[Long]("user_id")
    def tokenHash = column[String]("token_hash")
    def expiresAt = column[Instant]("expires_at")
    def used      = column[Boolean]("used")
    def * = (id, userId, tokenHash, expiresAt, used)
  }

  private val tokens = TableQuery[TokensTable]

  def create(userId: Long, tokenHash: String, expiresAt: Instant): Future[Unit] =
    db.run(tokens.map(t => (t.userId, t.tokenHash, t.expiresAt, t.used)) += ((userId, tokenHash, expiresAt, false))).map(_ => ())

  def findValid(tokenHash: String): Future[Option[Long]] =
    db.run(
      tokens
        .filter(t => t.tokenHash === tokenHash && !t.used && t.expiresAt > Instant.now())
        .map(_.userId)
        .result
        .headOption
    )

  def markUsed(tokenHash: String): Future[Unit] =
    db.run(tokens.filter(_.tokenHash === tokenHash).map(_.used).update(true)).map(_ => ())

  def deleteExpired(): Future[Unit] =
    db.run(tokens.filter(_.expiresAt < Instant.now()).delete).map(_ => ())
}
