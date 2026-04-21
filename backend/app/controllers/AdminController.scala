package controllers

import play.api.libs.json.Json
import play.api.mvc._
import services.MessageExpirationService

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class AdminController @Inject() (
  cc: ControllerComponents,
  expirationService: MessageExpirationService,
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private val secret = sys.env.getOrElse("ADMIN_SECRET", "dev")

  def triggerExpiration(): Action[AnyContent] = Action.async { request =>
    if (request.headers.get("X-Admin-Secret").contains(secret)) {
      expirationService.deleteExpired().map { count =>
        Ok(Json.obj("purged" -> count))
      }
    } else {
      scala.concurrent.Future.successful(Forbidden(Json.obj("error" -> "Forbidden")))
    }
  }
}
