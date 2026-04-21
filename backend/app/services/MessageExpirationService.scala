package services

import play.api.{Configuration, Logging}
import repositories.{AttachmentRepository, MessageRepository}

import java.nio.file.{Files, Paths}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class MessageExpirationService @Inject() (
  messageRepo: MessageRepository,
  attachmentRepo: AttachmentRepository,
  config: Configuration,
)(implicit ec: ExecutionContext) extends Logging {

  val expirationDays: Int = config.get[Int]("chat.messageExpirationDays")

  def deleteExpired(): Future[Int] = {
    for {
      expiredIds  <- messageRepo.findExpiredIds()
      attachments <- attachmentRepo.findByMessageIds(expiredIds)
      count       <- messageRepo.deleteExpired()
      _           = cleanFiles(attachments.map(_.storedPath))
    } yield count
  }

  private def cleanFiles(paths: Seq[String]): Unit =
    paths.foreach { p =>
      Try(Files.deleteIfExists(Paths.get(p))).fold(
        e => logger.warn(s"Failed to delete attachment file $p: ${e.getMessage}"),
        deleted => if (deleted) logger.debug(s"Deleted attachment file $p"),
      )
    }
}
