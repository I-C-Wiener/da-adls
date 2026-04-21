package repositories

import models.Conversation
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConversationRepository @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[PostgresProfile]
  import dbConfig._
  import profile.api._

  private class ConversationsTable(tag: Tag) extends Table[Conversation](tag, "conversations") {
    def id               = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def roomId           = column[Option[Long]]("room_id")
    def conversationType = column[String]("conversation_type")
    def createdAt        = column[Instant]("created_at")
    def * = (id, roomId, conversationType, createdAt).mapTo[Conversation]
  }

  private val conversations = TableQuery[ConversationsTable]

  def findById(id: Long): Future[Option[Conversation]] =
    db.run(conversations.filter(_.id === id).result.headOption)

  def createForRoom(roomId: Long): Future[Conversation] = {
    val conv = Conversation(0L, Some(roomId), "room", Instant.now())
    db.run((conversations returning conversations.map(_.id) into ((c, id) => c.copy(id = id))) += conv)
  }

  def findByRoom(roomId: Long): Future[Option[Conversation]] =
    db.run(conversations.filter(_.roomId === roomId).result.headOption)

  def createForDm(): Future[Conversation] = {
    val conv = Conversation(0L, None, "dm", Instant.now())
    db.run((conversations returning conversations.map(_.id) into ((c, id) => c.copy(id = id))) += conv)
  }
}
