package controllers

import filters.AuthenticatedAction
import models.Attachment
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc._
import pubsub.{ActorRegistry, RedisPublisher}
import repositories.{ConversationRepository, DmConversationRepository, RoomRepository, UnreadRepository}
import services.{AttachmentService, JwtService}

import java.nio.file.Paths
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class AttachmentController @Inject() (
  cc: ControllerComponents,
  auth: AuthenticatedAction,
  attachmentService: AttachmentService,
  jwtService: JwtService,
  conversationRepository: ConversationRepository,
  roomRepo: RoomRepository,
  dmConversationRepository: DmConversationRepository,
  registry: ActorRegistry,
  redisPublisher: RedisPublisher,
  unreadRepo: UnreadRepository,
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def upload(): Action[MultipartFormData[TemporaryFile]] = auth.async(parse.multipartFormData) { request =>
    request.body.file("file") match {
      case None => Future.successful(BadRequest(Json.obj("error" -> "file is required")))
      case Some(filePart) =>
        val originalName = Paths.get(filePart.filename).getFileName.toString
        val mimeType = filePart.contentType
        val content   = request.body.dataParts.get("content").flatMap(_.headOption).filter(_.trim.nonEmpty)
        val comment   = request.body.dataParts.get("comment").flatMap(_.headOption).filter(_.trim.nonEmpty)
        val messageId = request.body.dataParts.get("messageId").flatMap(_.headOption).flatMap(s => Try(s.toLong).toOption)
        val conversationId = request.body.dataParts.get("conversationId").flatMap(_.headOption).flatMap(s => Try(s.toLong).toOption)

        messageId match {
          case Some(id) =>
            attachmentService.save(id, originalName, mimeType, filePart.ref, comment).map {
              case Right(attachment) => Created(Json.obj(
                "attachment" -> Json.obj(
                  "id" -> attachment.id,
                  "messageId" -> attachment.messageId,
                  "originalName" -> attachment.originalName,
                  "mimeType" -> attachment.mimeType,
                  "downloadUrl" -> s"/api/attachments/${attachment.id}"
                )
              ))
              case Left(error) => BadRequest(Json.obj("error" -> error))
            }
          case None =>
            conversationId match {
              case Some(convId) =>
                attachmentService.createMessageWithAttachment(convId, request.userId, content, originalName, mimeType, filePart.ref, comment).map {
                  case Right((message, attachment)) =>
                    publishReceivedEvent(message, attachment)
                    Created(Json.obj(
                      "message" -> Json.obj(
                        "id" -> message.id,
                        "conversationId" -> message.conversationId,
                        "senderId" -> message.senderId,
                        "content" -> message.content,
                        "replyToId" -> message.replyToId,
                        "isEdited" -> message.isEdited,
                        "isDeleted" -> message.isDeleted,
                        "createdAt" -> message.createdAt.toString,
                        "expiresAt" -> message.expiresAt.map(_.toString),
                        "attachments" -> Json.arr(Json.obj(
                          "id" -> attachment.id,
                          "originalName" -> attachment.originalName,
                          "mimeType" -> attachment.mimeType,
                          "downloadUrl" -> s"/api/attachments/${attachment.id}",
                          "fileSizeBytes" -> attachment.fileSizeBytes,
                          "comment" -> attachment.comment,
                        ))
                      )
                    ))
                  case Left(error) => BadRequest(Json.obj("error" -> error))
                }
              case None => Future.successful(BadRequest(Json.obj("error" -> "conversationId or messageId is required")))
            }
        }
    }
  }

  private def publishReceivedEvent(message: models.Message, attachment: Attachment): Unit = {
    conversationRepository.findById(message.conversationId).foreach {
      case Some(conv) =>
        val recipientsFut = conv.roomId match {
          case Some(roomId) => roomRepo.findMembers(roomId).map(_.map(_.userId))
          case None => dmConversationRepository.findByConversationId(message.conversationId).map {
            case Some((_, userA, userB)) => Seq(userA, userB)
            case None => Seq.empty
          }
        }

        recipientsFut.foreach { recipients =>
          val nonSenders = recipients.filterNot(_ == message.senderId)
          unreadRepo.increment(message.conversationId, nonSenders).foreach { newCounts =>
            newCounts.foreach { case (recipientId, count) =>
              registry.broadcastToRoom(Seq(recipientId),
                Json.obj("type" -> "unread_update", "conversationId" -> message.conversationId, "count" -> count))
            }
          }
          val event = Json.obj(
            "type" -> "message_received",
            "message" -> Json.obj(
              "id" -> message.id,
              "conversationId" -> message.conversationId,
              "senderId" -> message.senderId,
              "content" -> message.content,
              "replyToId" -> message.replyToId,
              "isEdited" -> message.isEdited,
              "isDeleted" -> message.isDeleted,
              "createdAt" -> message.createdAt.toString,
              "expiresAt" -> message.expiresAt.map(_.toString),
              "attachments" -> Json.arr(Json.obj(
                "id" -> attachment.id,
                "messageId" -> attachment.messageId,
                "originalName" -> attachment.originalName,
                "mimeType" -> attachment.mimeType,
                "downloadUrl" -> s"/api/attachments/${attachment.id}",
                "fileSizeBytes" -> attachment.fileSizeBytes,
                "comment" -> attachment.comment,
              ))
            ),
            "_recipients" -> recipients,
          )

          registry.broadcastToRoom(recipients, event)
          conv.roomId.foreach(roomId => redisPublisher.publish(redisPublisher.roomChannel(roomId), event))
        }
      case None => ()
    }
  }

  def download(id: Long): Action[AnyContent] = Action.async { request =>
    val tokenOpt =
      request.headers.get("Authorization").flatMap(_.split(" ").drop(1).headOption)
        .orElse(request.queryString.get("token").flatMap(_.headOption))

    tokenOpt.flatMap(jwtService.validateToken).map(_._1) match {
      case None => Future.successful(Unauthorized(Json.obj("error" -> "Authentication required")))
      case Some(userId) =>
        attachmentService.stream(id, userId).map {
          case Left("Forbidden") => Forbidden(Json.obj("error" -> "Forbidden"))
          case Left(_)           => NotFound(Json.obj("error" -> "Attachment not found"))
          case Right(dl) =>
            val isInline = dl.contentType.startsWith("image/") || dl.contentType.startsWith("video/")
            Ok.sendPath(dl.path, inline = isInline, fileName = _ => Some(dl.originalName))
              .as(dl.contentType)
        }
    }
  }
}
