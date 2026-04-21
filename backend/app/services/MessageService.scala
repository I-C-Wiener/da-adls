package services

import models.Message
import play.api.Configuration
import repositories.MessageRepository

import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MessageService @Inject() (
  messageRepo: MessageRepository,
  config: Configuration,
)(implicit ec: ExecutionContext) {

  private val expirationDays = config.get[Int]("chat.messageExpirationDays")

  def save(conversationId: Long, senderId: Long, content: String, replyToId: Option[Long]): Future[Message] = {
    val expiresAt = if (expirationDays > 0)
      Some(Instant.now().plus(expirationDays.toLong, ChronoUnit.DAYS))
    else None

    val msg = Message(
      id             = 0L,
      conversationId = conversationId,
      senderId       = senderId,
      content        = Some(content),
      replyToId      = replyToId,
      isEdited       = false,
      editedAt       = None,
      isDeleted      = false,
      createdAt      = Instant.now(),
      expiresAt      = expiresAt,
    )
    messageRepo.insert(msg)
  }

  def edit(messageId: Long, newContent: String, requesterId: Long): Future[Either[String, Message]] =
    messageRepo.findById(messageId).flatMap {
      case None                                     => Future.successful(Left("Message not found"))
      case Some(msg) if msg.senderId != requesterId => Future.successful(Left("Not allowed"))
      case Some(msg) =>
        val updated = msg.copy(content = Some(newContent), isEdited = true, editedAt = Some(Instant.now()))
        messageRepo.update(updated).map(Right(_))
    }

  def delete(messageId: Long, requesterId: Long): Future[Either[String, Unit]] =
    messageRepo.softDelete(messageId, requesterId).map {
      case 0 => Left("Message not found or not allowed")
      case _ => Right(())
    }

  def findById(id: Long): Future[Option[Message]] =
    messageRepo.findById(id)

  def adminDelete(messageId: Long): Future[Either[String, Unit]] =
    messageRepo.adminSoftDelete(messageId).map {
      case 0 => Left("Message not found")
      case _ => Right(())
    }

  def paginate(conversationId: Long, beforeId: Option[Long], limit: Int): Future[Seq[Message]] =
    messageRepo.findPage(conversationId, beforeId, limit)
}
