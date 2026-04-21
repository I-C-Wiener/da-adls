package filters

import play.api.libs.json.Json
import play.api.mvc._
import repositories.{UserRepository, UserSessionRepository}
import services.JwtService

import java.time.Instant
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UserRequest[A](val userId: Long, val sessionId: String, request: Request[A]) extends WrappedRequest[A](request)

class AuthenticatedAction @Inject() (
  bodyParser: BodyParser[AnyContent],
  jwtService: JwtService,
  userRepo: UserRepository,
  sessionRepo: UserSessionRepository,
)(implicit ec: ExecutionContext) extends ActionBuilder[UserRequest, AnyContent] {

  override val executionContext: ExecutionContext = ec
  override def parser: BodyParser[AnyContent]    = bodyParser

  override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
    request.headers.get("Authorization").flatMap(_.split(" ").drop(1).headOption) match {
      case Some(token) =>
        jwtService.validateToken(token) match {
          case Some((userId, sessionId)) =>
            sessionRepo.findBySessionId(sessionId).flatMap {
              case Some(session) if session.expiresAt.isAfter(Instant.now()) =>
                block(new UserRequest(userId, sessionId, request))
              case _ =>
                Future.successful(Results.Unauthorized(Json.obj("error" -> "Session expired or revoked")))
            }
          case None => Future.successful(Results.Unauthorized(Json.obj("error" -> "Authentication required")))
        }
      case None =>
        Future.successful(Results.Unauthorized(Json.obj("error" -> "Authentication required")))
    }
}
