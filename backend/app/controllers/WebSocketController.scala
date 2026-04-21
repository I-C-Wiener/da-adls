package controllers

import actors.WebSocketActor
import akka.actor.ActorSystem
import akka.stream.Materializer
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import pubsub.{ActorRegistry, RedisPublisher}
import repositories.{ConversationRepository, DmConversationRepository, RoomRepository, UnreadRepository, UserRepository, UserSessionRepository}
import services.{JwtService, MessageService, PresenceService}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class WebSocketController @Inject() (
  cc: ControllerComponents,
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
)(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) extends AbstractController(cc) {

  def socket(): WebSocket = WebSocket.accept[JsValue, JsValue] { _ =>
    ActorFlow.actorRef { out =>
      WebSocketActor.props(out, jwtService, registry, messageService, redisPublisher, roomRepo, convRepo, presenceService, userRepo, sessionRepo, dmRepo, unreadRepo)
    }
  }
}
