package controllers

import play.api.libs.json.Json
import play.api.mvc._

import javax.inject.{Inject, Singleton}

@Singleton
class HealthController @Inject() (cc: ControllerComponents) extends AbstractController(cc) {
  def health(): Action[AnyContent] = Action {
    Ok(Json.obj("status" -> "ok"))
  }
}
