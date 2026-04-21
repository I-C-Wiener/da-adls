package controllers

import filters.AuthenticatedAction
import models.{MemberRole, RoomVisibility}
import play.api.libs.json._
import play.api.mvc._
import play.api.libs.json.Json
import pubsub.ActorRegistry
import repositories.{ConversationRepository, RoomRepository, UserRepository}
import services.RoomService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RoomController @Inject() (
  cc: ControllerComponents,
  auth: AuthenticatedAction,
  roomService: RoomService,
  roomRepo: RoomRepository,
  userRepo: UserRepository,
  convRepo: ConversationRepository,
  registry: ActorRegistry,
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def list(search: Option[String]): Action[AnyContent] = auth.async { _ =>
    roomService.listPublic(search).flatMap { rooms =>
      Future.sequence(rooms.map { r =>
        for {
          conv  <- convRepo.findByRoom(r.id)
          count <- roomRepo.countMembers(r.id)
        } yield roomJson(r, conv.map(_.id), memberCount = Some(count))
      }).map(jsons => Ok(Json.toJson(jsons)))
    }
  }

  def mine(): Action[AnyContent] = auth.async { request =>
    roomService.listForMember(request.userId).flatMap(roomsWithConversations)
  }

  private def roomsWithConversations(rooms: Seq[models.Room]): Future[Result] =
    Future.sequence(rooms.map { r =>
      convRepo.findByRoom(r.id).map(conv => roomJson(r, conv.map(_.id)))
    }).map(jsons => Ok(Json.toJson(jsons)))

  def create(): Action[JsValue] = auth.async(parse.json) { request =>
    val name        = (request.body \ "name").asOpt[String].map(_.trim).filter(_.nonEmpty)
    val description = (request.body \ "description").asOpt[String]
    val visibility  = (request.body \ "visibility").asOpt[String]
      .map { case "private" => RoomVisibility.Private; case _ => RoomVisibility.Public }
      .getOrElse(RoomVisibility.Public)

    name match {
      case None => Future.successful(BadRequest(Json.obj("error" -> "name is required")))
      case Some(n) =>
        roomService.create(n, description, visibility, request.userId).map {
          case Right(room) => Created(roomJson(room))
          case Left(err)   => Conflict(Json.obj("error" -> err))
        }
    }
  }

  def get(id: Long): Action[AnyContent] = auth.async { _ =>
    roomRepo.findById(id).flatMap {
      case None    => Future.successful(NotFound(Json.obj("error" -> "Room not found")))
      case Some(r) => convRepo.findByRoom(id).map(conv => Ok(roomJson(r, conv.map(_.id))))
    }
  }

  def update(id: Long): Action[JsValue] = auth.async(parse.json) { request =>
    val name        = (request.body \ "name").asOpt[String].map(_.trim).filter(_.nonEmpty)
    val description = (request.body \ "description").asOpt[String]
    val visibility  = (request.body \ "visibility").asOpt[String]
      .map { case "private" => RoomVisibility.Private; case _ => RoomVisibility.Public }
      .getOrElse(RoomVisibility.Public)

    name match {
      case None => Future.successful(BadRequest(Json.obj("error" -> "name is required")))
      case Some(n) =>
        roomService.update(id, n, description, visibility, request.userId).map {
          case Right(_)  => Ok(Json.obj("message" -> "updated"))
          case Left(err) => Forbidden(Json.obj("error" -> err))
        }
    }
  }

  def delete(id: Long): Action[AnyContent] = auth.async { request =>
    roomRepo.findMembers(id).flatMap { members =>
      roomService.delete(id, request.userId).map {
        case Right(_) =>
          val kickEvent = Json.obj("type" -> "kicked_from_room", "roomId" -> id)
          members.foreach(m => registry.kickUser(m.userId, kickEvent))
          NoContent
        case Left(err) => Forbidden(Json.obj("error" -> err))
      }
    }
  }

  def join(id: Long): Action[AnyContent] = auth.async { request =>
    roomService.join(id, request.userId).map {
      case Right(_)  => Ok(Json.obj("message" -> "joined"))
      case Left(err) => Forbidden(Json.obj("error" -> err))
    }
  }

  def leave(id: Long): Action[AnyContent] = auth.async { request =>
    roomService.leave(id, request.userId).map {
      case Right(_)  => NoContent
      case Left(err) => Forbidden(Json.obj("error" -> err))
    }
  }

  def members(id: Long): Action[AnyContent] = auth.async { _ =>
    roomService.getMembers(id).flatMap { roomMembers =>
      val userIds = roomMembers.map(_.userId)
      Future.sequence(userIds.map(userRepo.findById)).map { users =>
        val userMap = users.flatten.map(u => u.id -> u).toMap
        Ok(Json.toJson(roomMembers.map { m =>
          val u = userMap.get(m.userId)
          Json.obj(
            "userId"      -> m.userId,
            "username"    -> u.map(_.username),
            "displayName" -> u.flatMap(_.displayName),
            "avatarUrl"   -> u.flatMap(_.avatarUrl),
            "role"        -> roleStr(m.role),
            "joinedAt"    -> m.joinedAt.toString,
          )
        }))
      }
    }
  }

  def updateRole(id: Long, userId: Long): Action[JsValue] = auth.async(parse.json) { request =>
    (request.body \ "role").asOpt[String] match {
      case None => Future.successful(BadRequest(Json.obj("error" -> "role is required")))
      case Some(r) =>
        val newRole = r match {
          case "admin" => MemberRole.Admin
          case _       => MemberRole.Member
        }
        roomService.changeRole(id, userId, newRole, request.userId).map {
          case Right(_)  => Ok(Json.obj("message" -> "role updated"))
          case Left(err) => Forbidden(Json.obj("error" -> err))
        }
    }
  }

  def ban(id: Long, userId: Long): Action[AnyContent] = auth.async { request =>
    roomService.ban(id, userId, request.userId).map {
      case Right(_) =>
        registry.kickUser(userId, Json.obj("type" -> "kicked_from_room", "roomId" -> id))
        Ok(Json.obj("message" -> "banned"))
      case Left(err) => Forbidden(Json.obj("error" -> err))
    }
  }

  def unban(id: Long, userId: Long): Action[AnyContent] = auth.async { request =>
    roomService.unban(id, userId, request.userId).map {
      case Right(_)  => NoContent
      case Left(err) => Forbidden(Json.obj("error" -> err))
    }
  }

  def listBans(id: Long): Action[AnyContent] = auth.async { request =>
    roomRepo.getMemberRole(id, request.userId).flatMap {
      case Some(MemberRole.Owner) | Some(MemberRole.Admin) =>
        roomService.listBans(id).flatMap { bans =>
          val userIds = (bans.map(_.userId) ++ bans.map(_.bannedBy)).distinct
          Future.sequence(userIds.map(userRepo.findById)).map { users =>
            val uMap = users.flatten.map(u => u.id -> u).toMap
            Ok(Json.toJson(bans.map { b =>
              Json.obj(
                "userId"          -> b.userId,
                "username"        -> uMap.get(b.userId).map(_.username),
                "bannedBy"        -> b.bannedBy,
                "bannedByUsername"-> uMap.get(b.bannedBy).map(_.username),
                "bannedAt"        -> b.bannedAt.toString,
              )
            }))
          }
        }
      case _ => Future.successful(Forbidden(Json.obj("error" -> "Insufficient permissions")))
    }
  }

  def invite(id: Long): Action[JsValue] = auth.async(parse.json) { request =>
    (request.body \ "username").asOpt[String].map(_.trim).filter(_.nonEmpty) match {
      case None => Future.successful(BadRequest(Json.obj("error" -> "username is required")))
      case Some(username) =>
        roomService.invite(id, username, request.userId).map {
          case Right(_)  => Created(Json.obj("message" -> "invited"))
          case Left(err) => Forbidden(Json.obj("error" -> err))
        }
    }
  }

  def listInvitations(id: Long): Action[AnyContent] = auth.async { request =>
    roomRepo.getMemberRole(id, request.userId).flatMap {
      case Some(MemberRole.Owner) | Some(MemberRole.Admin) =>
        roomService.listInvitations(id).flatMap { invs =>
          val userIds = (invs.map(_.invitedUserId) ++ invs.map(_.invitedBy)).distinct
          Future.sequence(userIds.map(userRepo.findById)).map { users =>
            val uMap = users.flatten.map(u => u.id -> u).toMap
            Ok(Json.toJson(invs.map { inv =>
              Json.obj(
                "id"                -> inv.id,
                "invitedUserId"     -> inv.invitedUserId,
                "invitedUsername"   -> uMap.get(inv.invitedUserId).map(_.username),
                "invitedBy"         -> inv.invitedBy,
                "invitedByUsername" -> uMap.get(inv.invitedBy).map(_.username),
                "status"            -> inv.status,
                "createdAt"         -> inv.createdAt.toString,
              )
            }))
          }
        }
      case _ => Future.successful(Forbidden(Json.obj("error" -> "Insufficient permissions")))
    }
  }

  def myInvitations(): Action[AnyContent] = auth.async { request =>
    roomService.myInvitations(request.userId).flatMap { invs =>
      val roomIds = invs.map(_.roomId).distinct
      Future.sequence(roomIds.map(roomRepo.findById)).map { rooms =>
        val roomMap = rooms.flatten.map(r => r.id -> r).toMap
        Ok(Json.toJson(invs.map { inv =>
          val room = roomMap.get(inv.roomId)
          Json.obj(
            "id"       -> inv.id,
            "roomId"   -> inv.roomId,
            "roomName" -> room.map(_.name),
            "status"   -> inv.status,
            "createdAt"-> inv.createdAt.toString,
          )
        }))
      }
    }
  }

  private def roomJson(r: models.Room, conversationId: Option[Long] = None, memberCount: Option[Int] = None): JsValue = Json.obj(
    "id"             -> r.id,
    "name"           -> r.name,
    "description"    -> r.description,
    "visibility"     -> (if (r.visibility == RoomVisibility.Public) "public" else "private"),
    "ownerId"        -> r.ownerId,
    "createdAt"      -> r.createdAt.toString,
    "conversationId" -> conversationId,
    "memberCount"    -> memberCount,
  )

  private def roleStr(role: MemberRole): String = role match {
    case MemberRole.Owner  => "owner"
    case MemberRole.Admin  => "admin"
    case MemberRole.Member => "member"
  }
}
