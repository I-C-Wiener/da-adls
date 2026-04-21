package controllers

import filters.AuthenticatedAction
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import repositories.{ConversationRepository, DmConversationRepository, FriendshipRepository, UserRepository}
import services.ContactService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ContactController @Inject() (
  cc: ControllerComponents,
  auth: AuthenticatedAction,
  contactService: ContactService,
  userRepo: UserRepository,
  convRepo: ConversationRepository,
  dmRepo: DmConversationRepository,
  friendshipRepo: FriendshipRepository,
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def list(): Action[AnyContent] = auth.async { request =>
    for {
      friends  <- contactService.listFriends(request.userId)
      pending  <- contactService.pendingRequests(request.userId)
      userIds  =  (friends.map(_.userId) ++ pending.map(f => if (f.requesterId == request.userId) f.addresseeId else f.requesterId)).distinct
      users    <- Future.sequence(userIds.map(userRepo.findById)).map(_.flatten.map(u => u.id -> u).toMap)
    } yield {
      val friendsJson = friends.map { fi =>
        val u = users.get(fi.userId)
        Json.obj(
          "userId"         -> fi.userId,
          "username"       -> u.map(_.username),
          "displayName"    -> u.flatMap(_.displayName),
          "avatarUrl"      -> u.flatMap(_.avatarUrl),
          "friendshipId"   -> fi.friendshipId,
          "status"         -> fi.status,
          "conversationId" -> fi.conversationId,
        )
      }
      val pendingJson = pending.map { f =>
        val otherId = if (f.requesterId == request.userId) f.addresseeId else f.requesterId
        val u = users.get(otherId)
        Json.obj(
          "friendshipId" -> f.id,
          "userId"       -> otherId,
          "username"     -> u.map(_.username),
          "displayName"  -> u.flatMap(_.displayName),
          "direction"    -> (if (f.requesterId == request.userId) "sent" else "received"),
          "status"       -> f.status,
          "message"      -> f.message,
        )
      }
      Ok(Json.obj("friends" -> friendsJson, "pending" -> pendingJson))
    }
  }

  def sendRequest(): Action[JsValue] = auth.async(parse.json) { request =>
    val userIdOpt   = (request.body \ "userId").asOpt[Long]
    val usernameOpt = (request.body \ "username").asOpt[String]
    val messageOpt  = (request.body \ "message").asOpt[String].map(_.trim).filter(_.nonEmpty)

    val resolveTarget: Future[Either[String, Long]] = userIdOpt match {
      case Some(id) => Future.successful(Right(id))
      case None => usernameOpt match {
        case None => Future.successful(Left("userId or username is required"))
        case Some(uname) =>
          userRepo.findByUsername(uname).map {
            case Some(u) => Right(u.id)
            case None    => Left(s"User '$uname' not found")
          }
      }
    }

    resolveTarget.flatMap {
      case Left(err) => Future.successful(BadRequest(Json.obj("error" -> err)))
      case Right(targetId) if targetId == request.userId =>
        Future.successful(BadRequest(Json.obj("error" -> "Cannot send request to yourself")))
      case Right(targetId) =>
        contactService.sendRequest(request.userId, targetId, messageOpt).map {
          case Right(f) => Created(Json.obj("friendshipId" -> f.id, "status" -> f.status))
          case Left(e)  => Conflict(Json.obj("error" -> e))
        }
    }
  }

  def respondRequest(id: Long): Action[JsValue] = auth.async(parse.json) { request =>
    (request.body \ "accept").asOpt[Boolean] match {
      case None => Future.successful(BadRequest(Json.obj("error" -> "accept is required")))
      case Some(accept) =>
        contactService.respond(id, request.userId, accept).map {
          case Right(f) => Ok(Json.obj("friendshipId" -> f.id, "status" -> f.status))
          case Left(e)  => Forbidden(Json.obj("error" -> e))
        }
    }
  }

  def remove(userId: Long): Action[AnyContent] = auth.async { request =>
    contactService.remove(request.userId, userId).map {
      case Right(_) => NoContent
      case Left(e)  => NotFound(Json.obj("error" -> e))
    }
  }

  def block(userId: Long): Action[AnyContent] = auth.async { request =>
    contactService.block(request.userId, userId).map {
      case Right(_) => NoContent
      case Left(e)  => BadRequest(Json.obj("error" -> e))
    }
  }

  def unblock(userId: Long): Action[AnyContent] = auth.async { request =>
    contactService.unblock(request.userId, userId).map(_ => NoContent)
  }

  def getDmConversation(userId: Long): Action[AnyContent] = auth.async { request =>
    if (userId == request.userId)
      Future.successful(BadRequest(Json.obj("error" -> "Cannot DM yourself")))
    else
      userRepo.findById(userId).flatMap {
        case None => Future.successful(NotFound(Json.obj("error" -> "User not found")))
        case Some(u) =>
          for {
            banned1 <- friendshipRepo.isBanned(request.userId, userId)
            banned2 <- friendshipRepo.isBanned(userId, request.userId)
            result  <- if (banned1 || banned2)
              Future.successful(Forbidden(Json.obj("error" -> "Cannot message this user")))
            else dmRepo.findByUsers(request.userId, userId).flatMap {
              case Some(convId) =>
                Future.successful(Ok(Json.obj(
                  "conversationId" -> convId,
                  "userId" -> userId,
                  "username" -> u.username,
                  "displayName" -> u.displayName,
                )))
              case None =>
                friendshipRepo.findBetween(request.userId, userId).flatMap {
                  case Some(f) if f.status == "accepted" =>
                    convRepo.createForDm().flatMap { conv =>
                      dmRepo.create(conv.id, request.userId, userId).map { _ =>
                        Ok(Json.obj(
                          "conversationId" -> conv.id,
                          "userId" -> userId,
                          "username" -> u.username,
                          "displayName" -> u.displayName,
                        ))
                      }
                    }
                  case _ =>
                    Future.successful(Forbidden(Json.obj("error" -> "You must be friends to start a direct message")))
                }
            }
          } yield result
      }
  }
}
