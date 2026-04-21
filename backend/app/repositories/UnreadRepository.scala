package repositories

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.PostgresProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UnreadRepository @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[PostgresProfile] {

  import profile.api._

  def increment(conversationId: Long, userIds: Seq[Long]): Future[Map[Long, Int]] = {
    if (userIds.isEmpty) return Future.successful(Map.empty)
    val actions = userIds.map { uid =>
      sql"""
        INSERT INTO unread_counts (user_id, conversation_id, count)
        VALUES (${uid}, ${conversationId}, 1)
        ON CONFLICT (user_id, conversation_id) DO UPDATE SET count = unread_counts.count + 1
        RETURNING user_id, count
      """.as[(Long, Int)].headOption
    }
    db.run(DBIO.sequence(actions)).map(_.flatten.toMap)
  }

  def reset(conversationId: Long, userId: Long, lastMessageId: Long): Future[Unit] =
    db.run(sqlu"""
      INSERT INTO unread_counts (user_id, conversation_id, count, last_read_message_id)
      VALUES (${userId}, ${conversationId}, 0, ${lastMessageId})
      ON CONFLICT (user_id, conversation_id) DO UPDATE
        SET count = 0, last_read_message_id = ${lastMessageId}
    """).map(_ => ())

  def getForUser(userId: Long): Future[Map[Long, Int]] =
    db.run(
      sql"SELECT conversation_id, count FROM unread_counts WHERE user_id = ${userId} AND count > 0"
        .as[(Long, Int)]
    ).map(_.toMap)
}
