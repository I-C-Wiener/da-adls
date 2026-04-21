package services

import models.{Attachment, Message}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}
import repositories.{AttachmentRepository, ConversationRepository, DmConversationRepository, MessageRepository, RoomRepository}

import java.nio.file.{Files, Path}
import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AttachmentServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar {

  private val tempDir = Files.createTempDirectory("attachment-service-spec")
  private val config = Configuration(
    "uploads.dir" -> tempDir.toString,
    "uploads.maxFileMb" -> 1,
    "uploads.maxImageMb" -> 1,
    "chat.messageExpirationDays" -> 30,
  )

  private def makeMessage(id: Long, conversationId: Long, senderId: Long) =
    Message(id, conversationId, senderId, Some("hello"), None, isEdited = false, None, isDeleted = false, Instant.now(), None)

  "AttachmentService" should {

    "save file attachment when under file limits" in {
      val attachmentRepo = mock[AttachmentRepository]
      val messageRepo = mock[MessageRepository]
      val convRepo = mock[ConversationRepository]
      val roomRepo = mock[RoomRepository]
      val dmRepo = mock[DmConversationRepository]

      val msg = makeMessage(1L, 42L, senderId = 10L)
      when(messageRepo.findById(1L)).thenReturn(Future.successful(Some(msg)))
      when(attachmentRepo.create(any())).thenAnswer(inv => Future.successful(inv.getArgument[Attachment](0).copy(id = 1L)))

      val service = new AttachmentService(attachmentRepo, messageRepo, convRepo, roomRepo, dmRepo, config)
      val file = Files.createTempFile(tempDir, "upload", ".txt")
      Files.write(file, "test data".getBytes)
      val tempFile = SingletonTemporaryFileCreator.create(file)

      val result = service.save(1L, "upload.txt", Some("text/plain"), tempFile, None).futureValue

      result mustBe a[Right[_, _]]
      result.toOption.get.originalName mustBe "upload.txt"
      result.toOption.get.fileSizeBytes must be > 0L
    }

    "reject file when file size exceeds limit" in {
      val attachmentRepo = mock[AttachmentRepository]
      val messageRepo = mock[MessageRepository]
      val convRepo = mock[ConversationRepository]
      val roomRepo = mock[RoomRepository]
      val dmRepo = mock[DmConversationRepository]

      val msg = makeMessage(1L, 42L, senderId = 10L)
      when(messageRepo.findById(1L)).thenReturn(Future.successful(Some(msg)))

      val service = new AttachmentService(attachmentRepo, messageRepo, convRepo, roomRepo, dmRepo, config)
      val file = Files.createTempFile(tempDir, "large", ".bin")
      Files.write(file, Array.fill(2 * 1024 * 1024)(0.toByte))
      val tempFile = SingletonTemporaryFileCreator.create(file)

      service.save(1L, "large.bin", Some("application/octet-stream"), tempFile, None).futureValue mustBe Left("File size exceeds the limit")
    }

    "reject image when image size exceeds limit" in {
      val attachmentRepo = mock[AttachmentRepository]
      val messageRepo = mock[MessageRepository]
      val convRepo = mock[ConversationRepository]
      val roomRepo = mock[RoomRepository]
      val dmRepo = mock[DmConversationRepository]

      when(messageRepo.findById(1L)).thenReturn(Future.successful(Some(makeMessage(1L, 42L, 10L))))

      // maxFileMb=20, maxImageMb=1 so a 2MB image passes file check but fails image check
      val imageConfig = Configuration(
        "uploads.dir" -> tempDir.toString,
        "uploads.maxFileMb" -> 20,
        "uploads.maxImageMb" -> 1,
        "chat.messageExpirationDays" -> 30,
      )
      val service = new AttachmentService(attachmentRepo, messageRepo, convRepo, roomRepo, dmRepo, imageConfig)
      val file = Files.createTempFile(tempDir, "bigimg", ".jpg")
      // Write a valid JPEG header (FFD8) followed by filler to exceed 1MB image limit
      val header = Array(0xff.toByte, 0xd8.toByte)
      val body   = Array.fill(2 * 1024 * 1024 - 2)(0.toByte)
      Files.write(file, header ++ body)
      val tempFile = SingletonTemporaryFileCreator.create(file)

      service.save(1L, "big.jpg", Some("image/jpeg"), tempFile, None).futureValue mustBe Left("Image size exceeds the limit")
    }

    "forbid stream for non-member" in {
      val attachmentRepo = mock[AttachmentRepository]
      val messageRepo = mock[MessageRepository]
      val convRepo = mock[ConversationRepository]
      val roomRepo = mock[RoomRepository]
      val dmRepo = mock[DmConversationRepository]

      val attachment = Attachment(2L, 2L, "secret.txt", tempDir.resolve("secret.txt").toString, Some("text/plain"), 5L, None, Instant.now())
      val msg = makeMessage(2L, 101L, senderId = 99L)
      when(attachmentRepo.findById(2L)).thenReturn(Future.successful(Some(attachment)))
      when(messageRepo.findById(2L)).thenReturn(Future.successful(Some(msg)))
      when(convRepo.findById(101L)).thenReturn(Future.successful(Some(models.Conversation(101L, Some(201L), "room", Instant.now()))))
      when(roomRepo.isMember(201L, 55L)).thenReturn(Future.successful(false))

      val service = new AttachmentService(attachmentRepo, messageRepo, convRepo, roomRepo, dmRepo, config)

      service.stream(2L, requesterId = 55L).futureValue mustBe Left("Forbidden")
    }

    "stream attachment only for authorized conversation members" in {
      val attachmentRepo = mock[AttachmentRepository]
      val messageRepo = mock[MessageRepository]
      val convRepo = mock[ConversationRepository]
      val roomRepo = mock[RoomRepository]
      val dmRepo = mock[DmConversationRepository]

      val attachment = Attachment(1L, 1L, "file.txt", tempDir.resolve("file.txt").toString, Some("text/plain"), 5L, None, Instant.now())
      val msg = makeMessage(1L, 100L, senderId = 10L)
      when(attachmentRepo.findById(1L)).thenReturn(Future.successful(Some(attachment)))
      when(messageRepo.findById(1L)).thenReturn(Future.successful(Some(msg)))
      when(convRepo.findById(100L)).thenReturn(Future.successful(Some(models.Conversation(100L, Some(200L), "room", Instant.now()))))
      when(roomRepo.isMember(200L, 10L)).thenReturn(Future.successful(true))

      val service = new AttachmentService(attachmentRepo, messageRepo, convRepo, roomRepo, dmRepo, config)
      Files.write(Path.of(attachment.storedPath), "hello".getBytes)

      service.stream(1L, requesterId = 10L).futureValue match {
        case Right(download) =>
          download.originalName mustBe "file.txt"
          download.contentType mustBe "text/plain"
        case Left(_) => fail("Expected authorized download")
      }
    }
  }
}
