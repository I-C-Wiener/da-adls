package repositories

import models.PresenceStatus
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.PostgresProfile

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PresenceRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[PostgresProfile] {
  import profile.api._

  private class PresenceTable(tag: Tag) extends Table[(Long, String, Instant)](tag, "user_presence") {
    def userId    = column[Long]("user_id", O.PrimaryKey)
    def status    = column[String]("status")
    def updatedAt = column[Instant]("updated_at")
    def * = (userId, status, updatedAt)
  }

  private val presence = TableQuery[PresenceTable]

  def findStatus(userId: Long): Future[Option[PresenceStatus]] =
    db.run(presence.filter(_.userId === userId).map(_.status).result.headOption)
      .map(_.map(PresenceStatus.fromString))

  def upsertStatus(userId: Long, status: PresenceStatus): Future[Unit] =
    db.run(sqlu"""
      INSERT INTO user_presence (user_id, status, updated_at)
      SELECT ${userId}, ${PresenceStatus.toString(status)}, NOW()
      WHERE EXISTS (SELECT 1 FROM users WHERE id = ${userId})
      ON CONFLICT (user_id) DO UPDATE SET status = EXCLUDED.status, updated_at = EXCLUDED.updated_at
    """).map(_ => ())
}
