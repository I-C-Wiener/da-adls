package repositories

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.PostgresProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DmConversationRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[PostgresProfile] {
  import profile.api._

  private class DmConversationsTable(tag: Tag) extends Table[(Long, Long, Long, Boolean)](tag, "dm_conversations") {
    def conversationId = column[Long]("conversation_id", O.PrimaryKey)
    def userAId        = column[Long]("user_a_id")
    def userBId        = column[Long]("user_b_id")
    def frozen         = column[Boolean]("frozen")
    def * = (conversationId, userAId, userBId, frozen)
  }

  private val dmConversations = TableQuery[DmConversationsTable]

  private def orderedUsers(a: Long, b: Long): (Long, Long) = if (a <= b) (a, b) else (b, a)

  def findByUsers(userA: Long, userB: Long): Future[Option[Long]] = {
    val (low, high) = orderedUsers(userA, userB)
    db.run(
      dmConversations
        .filter(dc => dc.userAId === low && dc.userBId === high)
        .map(_.conversationId)
        .result
        .headOption,
    )
  }

  def create(conversationId: Long, userA: Long, userB: Long): Future[Unit] = {
    val (low, high) = orderedUsers(userA, userB)
    db.run(dmConversations += (conversationId, low, high, false)).map(_ => ())
  }

  def findByConversationId(conversationId: Long): Future[Option[(Long, Long, Long)]] =
    db.run(
      dmConversations.filter(_.conversationId === conversationId)
        .map(r => (r.conversationId, r.userAId, r.userBId))
        .result.headOption
    )

  def freeze(conversationId: Long): Future[Unit] =
    db.run(
      dmConversations.filter(_.conversationId === conversationId)
        .map(_.frozen).update(true)
    ).map(_ => ())

  def isFrozen(conversationId: Long): Future[Boolean] =
    db.run(
      dmConversations.filter(_.conversationId === conversationId)
        .map(_.frozen).result.headOption
    ).map(_.getOrElse(false))
}
