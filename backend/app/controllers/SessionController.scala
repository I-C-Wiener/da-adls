package controllers

import filters.AuthenticatedAction
import play.api.libs.json._
import play.api.mvc._
import pubsub.ActorRegistry
import repositories.UserSessionRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SessionController @Inject() (
  cc: ControllerComponents,
  auth: AuthenticatedAction,
  sessionRepo: UserSessionRepository,
  registry: ActorRegistry,
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def list(): Action[AnyContent] = auth.async { request =>
    sessionRepo.findByUserId(request.userId).map { sessions =>
      val json = sessions.map { s =>
        Json.obj(
          "sessionId"   -> s.tokenHash,
          "userAgent"   -> s.userAgent,
          "ipAddress"   -> s.ipAddress,
          "createdAt"   -> s.createdAt.toString,
          "lastSeen"    -> s.lastSeen.toString,
          "expiresAt"   -> s.expiresAt.toString,
          "isCurrent"   -> (s.tokenHash == request.sessionId),
        )
      }
      Ok(Json.toJson(json))
    }
  }

  def logout(sessionId: String): Action[AnyContent] = auth.async { request =>
    if (sessionId == request.sessionId) {
      // User is logging out current session
      sessionRepo.delete(sessionId).map { _ =>
        registry.kickBySessionId(request.userId, sessionId)
        NoContent
      }
    } else {
      // User is logging out a different session - verify ownership first
      sessionRepo.findBySessionId(sessionId).flatMap {
        case Some(session) if session.userId == request.userId =>
          sessionRepo.delete(sessionId).map { _ =>
            registry.kickBySessionId(request.userId, sessionId)
            NoContent
          }
        case _ => Future.successful(NotFound)
      }
    }
  }
}
