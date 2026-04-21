package controllers

import play.api.libs.json._
import play.api.mvc._
import services.AuthService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthController @Inject() (
  cc: ControllerComponents,
  authService: AuthService,
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def register(): Action[JsValue] = Action.async(parse.json) { request =>
    val email    = (request.body \ "email").asOpt[String]
    val username = (request.body \ "username").asOpt[String]
    val password = (request.body \ "password").asOpt[String]

    (email, username, password) match {
      case (Some(e), Some(u), Some(p)) =>
        val ua = request.headers.get("User-Agent")
        val ip = request.headers.get("X-Forwarded-For").map(_.split(",").head.trim).getOrElse(request.remoteAddress)
        authService.register(e, u, p, ua, ip).map {
          case Right(token) => Created(Json.obj("token" -> token))
          case Left(err)    => Conflict(Json.obj("error" -> err))
        }
      case _ => Future.successful(BadRequest(Json.obj("error" -> "email, username and password are required")))
    }
  }

  def login(): Action[JsValue] = Action.async(parse.json) { request =>
    val emailOrUsername = (request.body \ "login").asOpt[String]
    val password        = (request.body \ "password").asOpt[String]

    (emailOrUsername, password) match {
      case (Some(login), Some(pass)) =>
        val ip = request.headers.get("X-Forwarded-For").map(_.split(",").head.trim).getOrElse(request.remoteAddress)
        authService.login(login, pass, request.headers.get("User-Agent"), ip).map {
          case Right(token) => Ok(Json.obj("token" -> token))
          case Left(_)      => Unauthorized(Json.obj("error" -> "Invalid credentials"))
        }
      case _ => Future.successful(BadRequest(Json.obj("error" -> "login and password are required")))
    }
  }

  def logout(): Action[AnyContent] = Action.async { request =>
    request.headers.get("Authorization").flatMap(_.split(" ").drop(1).headOption) match {
      case Some(token) =>
        authService.logout(token).map(_ => NoContent)
      case None =>
        Future.successful(Unauthorized(Json.obj("error" -> "Authorization header missing")))
    }
  }

  def forgotPassword(): Action[JsValue] = Action.async(parse.json) { request =>
    (request.body \ "email").asOpt[String] match {
      case Some(email) =>
        authService.initiatePasswordReset(email).map {
          case Some(token) =>
            // No email server: return the reset token directly so the frontend can build the reset link
            Ok(Json.obj("message" -> "Reset token generated", "resetToken" -> token))
          case None =>
            Ok(Json.obj("message" -> "If the email exists, a reset link will be sent"))
        }
      case None =>
        Future.successful(BadRequest(Json.obj("error" -> "email is required")))
    }
  }

  def resetPassword(): Action[JsValue] = Action.async(parse.json) { request =>
    val token    = (request.body \ "token").asOpt[String]
    val password = (request.body \ "password").asOpt[String]

    (token, password) match {
      case (Some(t), Some(p)) =>
        authService.resetPassword(t, p).map {
          case Right(_)  => Ok(Json.obj("message" -> "Password updated"))
          case Left(err) => BadRequest(Json.obj("error" -> err))
        }
      case _ => Future.successful(BadRequest(Json.obj("error" -> "token and password are required")))
    }
  }
}
