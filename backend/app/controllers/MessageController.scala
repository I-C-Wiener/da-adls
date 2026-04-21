package controllers

import filters.AuthenticatedAction
import models.MemberRole
import play.api.libs.json._
import play.api.mvc._
import services.MessageService
import repositories.{AttachmentRepository, ConversationRepository, MessageRepository, RoomRepository, UserRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MessageController @Inject() (
  cc: ControllerComponents,
  auth: AuthenticatedAction,
  messageService: MessageService,
  attachmentRepository: AttachmentRepository,
  userRepository: UserRepository,
  messageRepository: MessageRepository,
  convRepo: ConversationRepository,
  roomRepo: RoomRepository,
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def list(conversationId: Long, before: Option[Long], limit: Option[Int]): Action[AnyContent] = auth.async { _ =>
    messageService.paginate(conversationId, before, limit.getOrElse(50).min(100)).flatMap { msgs =>
      val senderIds  = msgs.map(_.senderId).distinct
      val replyIds   = msgs.flatMap(_.replyToId).distinct
      for {
        names        <- userRepository.findByIds(senderIds)
        replyMsgs    <- messageRepository.findByIds(replyIds)
        replySenders <- userRepository.findByIds(replyMsgs.values.map(_.senderId).toSeq.distinct)
        jsons        <- Future.sequence(msgs.map(m => msgJson(m, names, replyMsgs, replySenders)))
      } yield Ok(Json.toJson(jsons))
    }
  }

  def send(conversationId: Long): Action[JsValue] = auth.async(parse.json) { request =>
    val content   = (request.body \ "content").asOpt[String].map(_.trim).filter(_.nonEmpty)
    val replyToId = (request.body \ "replyToId").asOpt[Long]
    content match {
      case None => Future.successful(BadRequest(Json.obj("error" -> "content is required")))
      case Some(text) =>
        messageService.save(conversationId, request.userId, text, replyToId).flatMap { msg =>
          userRepository.findByIds(Seq(msg.senderId)).flatMap(names => msgJson(msg, names, Map.empty, Map.empty).map(Created(_)))
        }
    }
  }

  def edit(id: Long): Action[JsValue] = auth.async(parse.json) { request =>
    (request.body \ "content").asOpt[String].map(_.trim).filter(_.nonEmpty) match {
      case None => Future.successful(BadRequest(Json.obj("error" -> "content is required")))
      case Some(text) =>
        messageService.edit(id, text, request.userId).flatMap {
          case Right(msg) => userRepository.findByIds(Seq(msg.senderId)).flatMap(names => msgJson(msg, names, Map.empty, Map.empty).map(Ok(_)))
          case Left(err)  => Future.successful(Forbidden(Json.obj("error" -> err)))
        }
    }
  }

  def delete(id: Long): Action[AnyContent] = auth.async { request =>
    messageService.delete(id, request.userId).flatMap {
      case Right(_)  => Future.successful(NoContent)
      case Left(_) =>
        // Author-delete failed — check if requester is a room admin
        messageService.findById(id).flatMap {
          case None => Future.successful(NotFound(Json.obj("error" -> "Message not found")))
          case Some(msg) =>
            convRepo.findById(msg.conversationId).flatMap {
              case None => Future.successful(Forbidden(Json.obj("error" -> "Not allowed")))
              case Some(conv) =>
                conv.roomId match {
                  case None => Future.successful(Forbidden(Json.obj("error" -> "Not allowed")))
                  case Some(roomId) =>
                    roomRepo.getMemberRole(roomId, request.userId).flatMap {
                      case Some(MemberRole.Owner) | Some(MemberRole.Admin) =>
                        messageService.adminDelete(id).map {
                          case Right(_)  => NoContent
                          case Left(err) => Forbidden(Json.obj("error" -> err))
                        }
                      case _ => Future.successful(Forbidden(Json.obj("error" -> "Not allowed")))
                    }
                }
            }
        }
    }
  }

  private def msgJson(
    msg: models.Message,
    names: Map[Long, String],
    replyMsgs: Map[Long, models.Message],
    replySenders: Map[Long, String],
  ): Future[JsValue] = {
    val senderName: String = names.getOrElse(msg.senderId, msg.senderId.toString)
    val replyObj: JsValue = msg.replyToId.flatMap(replyMsgs.get) match {
      case None => JsNull
      case Some(r) =>
        val rSender: String = replySenders.getOrElse(r.senderId, r.senderId.toString)
        Json.obj(
          "id"         -> r.id,
          "senderName" -> rSender,
          "content"    -> (if (r.isDeleted) JsNull else r.content),
        )
    }
    attachmentRepository.findByMessageId(msg.id).map { attachments =>
      Json.obj(
        "id"             -> msg.id,
        "conversationId" -> msg.conversationId,
        "senderId"       -> msg.senderId,
        "senderName"     -> senderName,
        "content"        -> msg.content,
        "replyToId"      -> msg.replyToId,
        "replyTo"        -> replyObj,
        "isEdited"       -> msg.isEdited,
        "isDeleted"      -> msg.isDeleted,
        "createdAt"      -> msg.createdAt.toString,
        "expiresAt"      -> msg.expiresAt.map(_.toString),
        "attachments"    -> attachments.map(att => Json.obj(
          "id"           -> att.id,
          "messageId"    -> att.messageId,
          "originalName" -> att.originalName,
          "mimeType"     -> att.mimeType,
          "fileSizeBytes"-> att.fileSizeBytes,
          "downloadUrl"  -> s"/api/attachments/${att.id}",
          "comment"      -> att.comment,
        ))
      )
    }
  }
}
