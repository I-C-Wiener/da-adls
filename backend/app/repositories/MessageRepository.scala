package repositories

import models.Message
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.PostgresProfile

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MessageRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[PostgresProfile] {
  import profile.api._

  private class MessagesTable(tag: Tag) extends Table[Message](tag, "messages") {
    def id             = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def conversationId = column[Long]("conversation_id")
    def senderId       = column[Long]("sender_id")
    def content        = column[Option[String]]("content")
    def replyToId      = column[Option[Long]]("reply_to_id")
    def isEdited       = column[Boolean]("is_edited")
    def editedAt       = column[Option[Instant]]("edited_at")
    def isDeleted      = column[Boolean]("is_deleted")
    def createdAt      = column[Instant]("created_at")
    def expiresAt      = column[Option[Instant]]("expires_at")
    def * = (id, conversationId, senderId, content, replyToId, isEdited, editedAt, isDeleted, createdAt, expiresAt).mapTo[Message]
  }

  private val messages = TableQuery[MessagesTable]

  def insert(message: Message): Future[Message] =
    db.run((messages returning messages.map(_.id) into ((m, id) => m.copy(id = id))) += message)

  def findById(id: Long): Future[Option[Message]] =
    db.run(messages.filter(m => m.id === id && !m.isDeleted).result.headOption)

  def findPage(conversationId: Long, beforeId: Option[Long], limit: Int): Future[Seq[Message]] = {
    val base     = messages.filter(m => m.conversationId === conversationId && !m.isDeleted)
    val filtered = beforeId.fold(base)(id => base.filter(_.id < id))
    db.run(filtered.sortBy(_.createdAt.desc).take(limit).result)
  }

  def update(message: Message): Future[Message] =
    db.run(messages.filter(_.id === message.id).update(message)).map(_ => message)

  def softDelete(id: Long, userId: Long): Future[Int] =
    db.run(messages.filter(m => m.id === id && m.senderId === userId).map(_.isDeleted).update(true))

  def adminSoftDelete(id: Long): Future[Int] =
    db.run(messages.filter(_.id === id).map(_.isDeleted).update(true))

  def findByIds(ids: Seq[Long]): Future[Map[Long, Message]] =
    if (ids.isEmpty) Future.successful(Map.empty)
    else db.run(messages.filter(_.id inSet ids).result).map(_.map(m => m.id -> m).toMap)

  def findExpiredIds(): Future[Seq[Long]] =
    db.run(messages.filter(m => m.expiresAt.isDefined && m.expiresAt < Instant.now()).map(_.id).result)

  def deleteExpired(): Future[Int] =
    db.run(messages.filter(m => m.expiresAt.isDefined && m.expiresAt < Instant.now()).delete)
}
