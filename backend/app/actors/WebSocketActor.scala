package actors

import akka.actor._
import models.PresenceStatus
import play.api.libs.json._
import pubsub.{ActorRegistry, RedisPublisher}
import repositories.{ConversationRepository, DmConversationRepository, RoomRepository, UnreadRepository, UserRepository, UserSessionRepository}
import services.{JwtService, MessageService, PresenceService}

import scala.concurrent.{ExecutionContext, Future}

object WebSocketActor {
  def props(
    out: ActorRef,
    jwtService: JwtService,
    registry: ActorRegistry,
    messageService: MessageService,
    redisPublisher: RedisPublisher,
    roomRepo: RoomRepository,
    convRepo: ConversationRepository,
    presenceService: PresenceService,
    userRepo: UserRepository,
    sessionRepo: UserSessionRepository,
    dmRepo: DmConversationRepository,
    unreadRepo: UnreadRepository,
  )(implicit ec: ExecutionContext): Props =
    Props(new WebSocketActor(out, jwtService, registry, messageService, redisPublisher, roomRepo, convRepo, presenceService, userRepo, sessionRepo, dmRepo, unreadRepo))

  case class Broadcast(json: JsValue)
}

class WebSocketActor(
  out: ActorRef,
  jwtService: JwtService,
  registry: ActorRegistry,
  messageService: MessageService,
  redisPublisher: RedisPublisher,
  roomRepo: RoomRepository,
  convRepo: ConversationRepository,
  presenceService: PresenceService,
  userRepo: UserRepository,
  sessionRepo: UserSessionRepository,
  dmRepo: DmConversationRepository,
  unreadRepo: UnreadRepository,
)(implicit ec: ExecutionContext) extends Actor with ActorLogging {

  import WebSocketActor._

  private var userId: Option[Long]      = None
  private var sessionId: Option[String] = None

  override def receive: Receive = {
    case json: JsValue =>
      (json \ "type").asOpt[String] match {
        case Some("auth")           => handleAuth(json)
        case Some("ping")           => handlePing(json)
        case Some("send_message")   => handleSendMessage(json)
        case Some("edit_message")   => handleEditMessage(json)
        case Some("delete_message") => handleDeleteMessage(json)
        case Some("mark_read")      => handleMarkRead(json)
        case Some("typing_start")   => handleTyping(json)
        case Some(unknown)          => sendError(s"Unknown type: $unknown")
        case None                   => sendError("Missing type field")
      }
    case Broadcast(json) => out ! json
  }

  private def handleAuth(json: JsValue): Unit =
    (json \ "token").asOpt[String].flatMap(jwtService.validateToken) match {
      case Some((uid, sid)) =>
        sessionRepo.findBySessionId(sid).foreach {
          case Some(_) =>
            userId = Some(uid)
            sessionId = Some(sid)
            registry.register(uid, sid, self)
            presenceService.computeAndBroadcast(uid)
            out ! Json.obj("type" -> "auth_ok", "userId" -> uid)
            registry.allConnectedUserIds().foreach { cid =>
              val status = registry.derivePresence(cid)
              out ! Json.obj("type" -> "presence_update", "userId" -> cid, "status" -> PresenceStatus.toString(status))
            }
            unreadRepo.getForUser(uid).foreach { counts =>
              counts.foreach { case (convId, count) =>
                out ! Json.obj("type" -> "unread_update", "conversationId" -> convId, "count" -> count)
              }
            }
          case None =>
            out ! Json.obj("type" -> "error", "code" -> "UNAUTHORIZED", "message" -> "Invalid session")
            context.stop(self)
        }
      case None =>
        out ! Json.obj("type" -> "error", "code" -> "UNAUTHORIZED", "message" -> "Invalid token")
        context.stop(self)
    }

  private def handlePing(json: JsValue): Unit = {
    val activeTab = (json \ "activeTab").asOpt[Boolean].getOrElse(true)
    for { uid <- userId; sid <- sessionId } {
      registry.updatePresence(uid, sid, activeTab)
      presenceService.computeAndBroadcast(uid)
      sessionRepo.updateLastSeen(sid, java.time.Instant.now())
    }
  }

  private def membersForConv(conv: models.Conversation): Future[Either[String, Seq[Long]]] =
    conv.roomId match {
      case Some(rid) =>
        roomRepo.findMembers(rid).map(ms => Right(ms.map(_.userId)))
      case None =>
        dmRepo.findByConversationId(conv.id).flatMap {
          case None => Future.successful(Left("DM conversation not found"))
          case Some((_, a, b)) =>
            dmRepo.isFrozen(conv.id).map {
              case true  => Left("This conversation is frozen")
              case false => Right(Seq(a, b))
            }
        }
    }

  private def handleSendMessage(json: JsValue): Unit = withAuth { uid =>
    val conversationId = (json \ "conversationId").asOpt[Long]
    val content        = (json \ "content").asOpt[String].map(_.trim).filter(_.nonEmpty)
    val replyToId      = (json \ "replyToId").asOpt[Long]

    (conversationId, content) match {
      case (Some(convId), Some(text)) =>
        convRepo.findById(convId).foreach {
          case None => sendError("Conversation not found")
          case Some(conv) =>
            membersForConv(conv).foreach {
              case Left(err) => sendError(err)
              case Right(memberIds) =>
                if (!memberIds.contains(uid)) sendError("Not a member")
                else messageService.save(convId, uid, text, replyToId).foreach { msg =>
                  val senderFut  = userRepo.findById(uid)
                  val replyFut   = msg.replyToId.fold(Future.successful(Option.empty[(models.Message, String)])) { rid =>
                    messageService.findById(rid).flatMap {
                      case None        => Future.successful(None)
                      case Some(reply) => userRepo.findById(reply.senderId).map { u =>
                        Some(reply -> u.map(_.username).getOrElse(reply.senderId.toString))
                      }
                    }
                  }
                  for { userOpt <- senderFut; replyOpt <- replyFut } {
                    val name = userOpt.map(_.username).getOrElse(uid.toString)
                    val event = Json.obj(
                      "type"    -> "message_received",
                      "message" -> messageJson(msg, name, replyOpt),
                      "_recipients" -> memberIds,
                    )
                    conv.roomId.foreach(rid => redisPublisher.publish(redisPublisher.roomChannel(rid), event))
                    registry.broadcastToRoom(memberIds, event)
                    val recipients = memberIds.filterNot(_ == uid)
                    unreadRepo.increment(convId, recipients).foreach { newCounts =>
                      newCounts.foreach { case (recipientId, count) =>
                        val unreadEvent = Json.obj("type" -> "unread_update", "conversationId" -> convId, "count" -> count)
                        registry.broadcastToRoom(Seq(recipientId), unreadEvent)
                      }
                    }
                  }
                }
            }
        }
      case _ => sendError("conversationId and content are required")
    }
  }

  private def handleEditMessage(json: JsValue): Unit = withAuth { uid =>
    val messageId = (json \ "messageId").asOpt[Long]
    val content   = (json \ "content").asOpt[String].map(_.trim).filter(_.nonEmpty)

    (messageId, content) match {
      case (Some(mid), Some(text)) =>
        messageService.edit(mid, text, uid).foreach {
          case Left(err) => sendError(err)
          case Right(msg) =>
            val event = Json.obj("type" -> "message_edited", "messageId" -> msg.id, "content" -> msg.content)
            convRepo.findById(msg.conversationId).foreach {
              case None => out ! event
              case Some(conv) =>
                membersForConv(conv).foreach {
                  case Left(_) => out ! event
                  case Right(memberIds) =>
                    val broadcast = event ++ Json.obj("_recipients" -> memberIds)
                    conv.roomId.foreach(rid => redisPublisher.publish(redisPublisher.roomChannel(rid), broadcast))
                    registry.broadcastToRoom(memberIds, broadcast)
                }
            }
        }
      case _ => sendError("messageId and content are required")
    }
  }

  private def handleDeleteMessage(json: JsValue): Unit = withAuth { uid =>
    (json \ "messageId").asOpt[Long] match {
      case None => sendError("messageId is required")
      case Some(mid) =>
        messageService.findById(mid).foreach {
          case None => sendError("Message not found")
          case Some(msg) =>
            val deleteFut: Future[Either[String, Unit]] =
              if (msg.senderId == uid) {
                messageService.delete(mid, uid)
              } else {
                convRepo.findById(msg.conversationId).flatMap {
                  case None => Future.successful(Left("Conversation not found"))
                  case Some(conv) =>
                    conv.roomId match {
                      case None => Future.successful(Left("Not allowed"))
                      case Some(rid) =>
                        roomRepo.getMemberRole(rid, uid).flatMap {
                          case Some(role) if role == models.MemberRole.Owner || role == models.MemberRole.Admin =>
                            messageService.adminDelete(mid)
                          case _ => Future.successful(Left("Not allowed"))
                        }
                    }
                }
              }
            deleteFut.foreach {
              case Left(err) => sendError(err)
              case Right(_) =>
                val event = Json.obj("type" -> "message_deleted", "messageId" -> mid)
                convRepo.findById(msg.conversationId).foreach {
                  case None => out ! event
                  case Some(conv) =>
                    membersForConv(conv).foreach {
                      case Left(_) => out ! event
                      case Right(memberIds) =>
                        val broadcast = event ++ Json.obj("_recipients" -> memberIds)
                        conv.roomId.foreach(rid => redisPublisher.publish(redisPublisher.roomChannel(rid), broadcast))
                        registry.broadcastToRoom(memberIds, broadcast)
                    }
                }
            }
        }
    }
  }

  private def handleMarkRead(json: JsValue): Unit = withAuth { uid =>
    val conversationId  = (json \ "conversationId").asOpt[Long]
    val lastMessageId   = (json \ "lastMessageId").asOpt[Long]
    (conversationId, lastMessageId) match {
      case (Some(convId), Some(msgId)) =>
        unreadRepo.reset(convId, uid, msgId).foreach { _ =>
          out ! Json.obj("type" -> "unread_update", "conversationId" -> convId, "count" -> 0)
        }
      case _ => ()
    }
  }

  private def handleTyping(json: JsValue): Unit = withAuth { uid =>
    (json \ "conversationId").asOpt[Long].foreach { convId =>
      val event = Json.obj("type" -> "typing", "conversationId" -> convId, "userId" -> uid)
      out ! event
    }
  }

  private def withAuth(f: Long => Unit): Unit = userId match {
    case Some(uid) => f(uid)
    case None      => sendError("Not authenticated")
  }

  private def sendError(msg: String): Unit =
    out ! Json.obj("type" -> "error", "message" -> msg)

  private def messageJson(
    msg: models.Message,
    senderName: String,
    replyOpt: Option[(models.Message, String)] = None,
  ): JsValue = Json.obj(
    "id"             -> msg.id,
    "conversationId" -> msg.conversationId,
    "senderId"       -> msg.senderId,
    "senderName"     -> senderName,
    "content"        -> msg.content,
    "replyToId"      -> msg.replyToId,
    "replyTo"        -> replyOpt.map { case (r, name) =>
      Json.obj("id" -> r.id, "senderName" -> name, "content" -> r.content)
    },
    "isEdited"       -> msg.isEdited,
    "isDeleted"      -> msg.isDeleted,
    "createdAt"      -> msg.createdAt.toString,
    "attachments"    -> Json.arr()
  )

  override def postStop(): Unit =
    for { uid <- userId; sid <- sessionId } {
      registry.unregister(uid, sid)
      presenceService.computeAndBroadcast(uid)
    }
}
