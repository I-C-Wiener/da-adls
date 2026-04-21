package controllers

import filters.AuthenticatedAction
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import repositories.UserRepository
import services.AuthService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ProfileController @Inject() (
  cc: ControllerComponents,
  auth: AuthenticatedAction,
  authService: AuthService,
  userRepo: UserRepository,
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def get(): Action[AnyContent] = auth.async { request =>
    userRepo.findById(request.userId).map {
      case Some(user) => Ok(Json.obj(
        "id"          -> user.id,
        "email"       -> user.email,
        "username"    -> user.username,
        "displayName" -> user.displayName,
        "avatarUrl"   -> user.avatarUrl,
      ))
      case None => NotFound(Json.obj("error" -> "User not found"))
    }
  }

  def changePassword(): Action[JsValue] = auth.async(parse.json) { request =>
    val oldPassword = (request.body \ "oldPassword").asOpt[String]
    val newPassword = (request.body \ "newPassword").asOpt[String]
    (oldPassword, newPassword) match {
      case (Some(old), Some(next)) if next.length >= 6 =>
        authService.changePassword(request.userId, old, next).map {
          case Right(_)  => Ok(Json.obj("message" -> "Password updated"))
          case Left(err) => BadRequest(Json.obj("error" -> err))
        }
      case (_, Some(next)) if next.length < 6 =>
        Future.successful(BadRequest(Json.obj("error" -> "Password must be at least 6 characters")))
      case _ =>
        Future.successful(BadRequest(Json.obj("error" -> "oldPassword and newPassword are required")))
    }
  }

  def deleteAccount(): Action[AnyContent] = auth.async { request =>
    authService.deleteAccount(request.userId).map(_ => NoContent)
  }

  def searchUsers(username: String): Action[AnyContent] = auth.async { request =>
    userRepo.findByUsername(username).map {
      case Some(user) if user.id != request.userId =>
        Ok(Json.obj("id" -> user.id, "username" -> user.username, "displayName" -> user.displayName))
      case Some(user) if user.id == request.userId =>
        BadRequest(Json.obj("error" -> "Cannot add yourself"))
      case Some(_) =>
        BadRequest(Json.obj("error" -> "Invalid user"))
      case None =>
        NotFound(Json.obj("error" -> "User not found"))
    }
  }
}
