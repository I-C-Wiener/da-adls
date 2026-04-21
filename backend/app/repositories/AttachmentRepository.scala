package repositories

import models.Attachment
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.PostgresProfile

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AttachmentRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[PostgresProfile] {
  import profile.api._

  private class AttachmentsTable(tag: Tag) extends Table[Attachment](tag, "attachments") {
    def id           = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def messageId    = column[Long]("message_id")
    def originalName = column[String]("original_name")
    def storedPath   = column[String]("stored_path")
    def mimeType     = column[Option[String]]("mime_type")
    def fileSizeBytes= column[Long]("file_size_bytes")
    def comment      = column[Option[String]]("comment")
    def uploadedAt   = column[Instant]("uploaded_at")
    def * = (id, messageId, originalName, storedPath, mimeType, fileSizeBytes, comment, uploadedAt).mapTo[Attachment]
  }

  private val attachments = TableQuery[AttachmentsTable]

  def create(attachment: Attachment): Future[Attachment] =
    db.run((attachments returning attachments.map(_.id) into ((a, id) => a.copy(id = id))) += attachment)

  def findById(id: Long): Future[Option[Attachment]] =
    db.run(attachments.filter(_.id === id).result.headOption)

  def findByMessageId(messageId: Long): Future[Seq[Attachment]] =
    db.run(attachments.filter(_.messageId === messageId).result)

  def findByMessageIds(messageIds: Seq[Long]): Future[Seq[Attachment]] =
    if (messageIds.isEmpty) Future.successful(Seq.empty)
    else db.run(attachments.filter(_.messageId.inSet(messageIds)).result)

  def findStoredPathsByConversation(conversationId: Long): Future[Seq[String]] =
    db.run(sql"""
      SELECT a.stored_path FROM attachments a
      JOIN messages m ON m.id = a.message_id
      WHERE m.conversation_id = $conversationId
    """.as[String])
}
