package services

import models.{Attachment, Message}
import play.api.Configuration
import play.api.libs.Files.TemporaryFile
import repositories.{AttachmentRepository, ConversationRepository, DmConversationRepository, MessageRepository, RoomRepository}

import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class AttachmentDownload(path: Path, contentType: String, originalName: String)

@javax.inject.Singleton
class AttachmentService @Inject()(
  attachmentRepository: AttachmentRepository,
  messageRepository: MessageRepository,
  conversationRepository: ConversationRepository,
  roomRepository: RoomRepository,
  dmConversationRepository: DmConversationRepository,
  config: Configuration
)(implicit ec: ExecutionContext) {

  private val uploadRoot = Paths.get(config.get[String]("uploads.dir"))
  private val maxFileSizeBytes = config.get[Int]("uploads.maxFileMb") * 1024 * 1024
  private val maxImageSizeBytes = config.get[Int]("uploads.maxImageMb") * 1024 * 1024
  private val expirationDays = config.get[Int]("chat.messageExpirationDays")

  def save(messageId: Long, originalName: String, mimeType: Option[String], temporaryFile: TemporaryFile, comment: Option[String]): Future[Either[String, Attachment]] = {
    val filePath = temporaryFile.path
    val fileSize = Files.size(filePath)

    if (fileSize > maxFileSizeBytes) {
      Future.successful(Left("File size exceeds the limit"))
    } else if (isImage(mimeType, filePath) && fileSize > maxImageSizeBytes) {
      Future.successful(Left("Image size exceeds the limit"))
    } else {
      messageRepository.findById(messageId).flatMap {
        case Some(message) =>
          val storedPath = storeFile(message.conversationId, originalName, filePath)
          val attachment = Attachment(
            id = 0,
            messageId = messageId,
            originalName = sanitizeFileName(originalName),
            storedPath = storedPath.toString,
            mimeType = mimeType,
            fileSizeBytes = fileSize,
            comment = comment,
            uploadedAt = Instant.now()
          )
          attachmentRepository.create(attachment).map(Right(_))
        case None => Future.successful(Left("Message not found"))
      }
    }
  }

  def createMessageWithAttachment(conversationId: Long, senderId: Long, content: Option[String], originalName: String, mimeType: Option[String], temporaryFile: TemporaryFile, comment: Option[String] = None): Future[Either[String, (Message, Attachment)]] = {
    val expiresAt = if (expirationDays > 0)
      Some(Instant.now().plus(expirationDays.toLong, java.time.temporal.ChronoUnit.DAYS))
    else None

    val message = Message(
      id = 0L,
      conversationId = conversationId,
      senderId = senderId,
      content = content,
      replyToId = None,
      isEdited = false,
      editedAt = None,
      isDeleted = false,
      createdAt = Instant.now(),
      expiresAt = expiresAt,
    )

    messageRepository.insert(message).flatMap { msg =>
      save(msg.id, originalName, mimeType, temporaryFile, comment).map {
        case Right(attachment) => Right(msg -> attachment)
        case Left(error) => Left(error)
      }
    }
  }

  def stream(attachmentId: Long, requesterId: Long): Future[Either[String, AttachmentDownload]] = {
    attachmentRepository.findById(attachmentId).flatMap {
      case Some(attachment) =>
        messageRepository.findById(attachment.messageId).flatMap {
          case Some(message) =>
            authorize(message.conversationId, requesterId).flatMap {
              case true =>
                val path = Paths.get(attachment.storedPath)
                if (Files.exists(path)) {
                  val contentType = attachment.mimeType.getOrElse("application/octet-stream")
                  Future.successful(Right(AttachmentDownload(path, contentType, attachment.originalName)))
                } else {
                  Future.successful(Left("File not found"))
                }
              case false => Future.successful(Left("Forbidden"))
            }
          case None => Future.successful(Left("Message not found"))
        }
      case None => Future.successful(Left("Attachment not found"))
    }
  }

  private def authorize(conversationId: Long, requesterId: Long): Future[Boolean] = {
    conversationRepository.findById(conversationId).flatMap {
      case Some(conversation) =>
        conversation.roomId match {
          case Some(roomId) => roomRepository.isMember(roomId, requesterId)
          case None => dmConversationRepository.findByConversationId(conversationId).map {
            case Some((_, userA, userB)) => userA == requesterId || userB == requesterId
            case None => false
          }
        }
      case None => Future.successful(false)
    }
  }

  private def storeFile(conversationId: Long, originalName: String, source: Path): Path = {
    val fileName = s"${UUID.randomUUID()}-${sanitizeFileName(originalName)}"
    val targetDir = uploadRoot.resolve(conversationId.toString)
    Files.createDirectories(targetDir)
    val target = targetDir.resolve(fileName)
    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    target
  }

  private def sanitizeFileName(filename: String): String = {
    Paths.get(filename).getFileName.toString.replaceAll("[^a-zA-Z0-9._-]", "_")
  }

  private def isImage(mimeType: Option[String], path: Path): Boolean = {
    mimeType.exists(_.startsWith("image/")) || isImageMagic(path)
  }

  private def isImageMagic(path: Path): Boolean = {
    val header = Files.readAllBytes(path).take(12)
    val headerHex = header.map(b => f"$b%02x").mkString
    headerHex.startsWith("ffd8") || headerHex.startsWith("89504e47") || headerHex.startsWith("47494638") || headerHex.startsWith("52494646")
  }
}
