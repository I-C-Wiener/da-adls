package services

import models._
import play.api.libs.json.Json
import pubsub.ActorRegistry
import repositories.{AttachmentRepository, ConversationRepository, RoomInvitationRepository, RoomRepository, UserRepository}

import java.nio.file.{Files, Paths}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RoomService @Inject() (
  roomRepo: RoomRepository,
  convRepo: ConversationRepository,
  userRepo: UserRepository,
  invitationRepo: RoomInvitationRepository,
  attachmentRepo: AttachmentRepository,
  registry: ActorRegistry,
)(implicit ec: ExecutionContext) {

  def create(name: String, description: Option[String], visibility: RoomVisibility, ownerId: Long): Future[Either[String, Room]] =
    userRepo.findById(ownerId).flatMap {
      case None => Future.successful(Left("User not found"))
      case Some(_) =>
        roomRepo.create(name, description, visibility, ownerId)
          .flatMap { room =>
            convRepo.createForRoom(room.id)
              .flatMap(_ => roomRepo.addMember(room.id, ownerId, MemberRole.Owner))
              .map(_ => Right(room))
          }
          .recover { case ex =>
            val msg = ex.getMessage
            play.api.Logger("RoomService").error(s"create failed: $msg", ex)
            if (msg.toLowerCase.contains("unique")) Left("Room name already taken")
            else Left(s"Could not create room: $msg")
          }
    }

  def delete(roomId: Long, requesterId: Long): Future[Either[String, Unit]] =
    roomRepo.findById(roomId).flatMap {
      case None => Future.successful(Left("Room not found"))
      case Some(room) if room.ownerId != requesterId =>
        Future.successful(Left("Only the owner can delete this room"))
      case Some(_) =>
        convRepo.findByRoom(roomId).flatMap { convOpt =>
          val fileCleanup: Future[Unit] = convOpt match {
            case Some(conv) =>
              attachmentRepo.findStoredPathsByConversation(conv.id).map { paths =>
                paths.foreach { p =>
                  try { Files.deleteIfExists(Paths.get(p)) } catch { case _: Exception => () }
                }
              }
            case None => Future.unit
          }
          fileCleanup.flatMap(_ => roomRepo.hardDelete(roomId)).map(Right(_))
        }
    }

  def update(roomId: Long, name: String, description: Option[String], visibility: RoomVisibility, requesterId: Long): Future[Either[String, Unit]] =
    roomRepo.findById(roomId).flatMap {
      case None => Future.successful(Left("Room not found"))
      case Some(room) if room.ownerId != requesterId =>
        Future.successful(Left("Only the owner can update this room"))
      case Some(_) =>
        roomRepo.update(roomId, name, description, visibility)
          .map(Right(_))
          .recover {
            case ex if ex.getMessage.toLowerCase.contains("unique") => Left("Room name already taken")
          }
    }

  def join(roomId: Long, userId: Long): Future[Either[String, Unit]] =
    roomRepo.findById(roomId).flatMap {
      case None => Future.successful(Left("Room not found"))
      case Some(room) if room.visibility == RoomVisibility.Private =>
        invitationRepo.findPending(roomId, userId).flatMap {
          case None => Future.successful(Left("This room is private. You need an invitation to join."))
          case Some(inv) =>
            roomRepo.isBanned(roomId, userId).flatMap {
              case true => Future.successful(Left("You are banned from this room"))
              case false =>
                invitationRepo.accept(inv.id)
                  .flatMap(_ => roomRepo.addMember(roomId, userId, MemberRole.Member))
                  .map(Right(_))
            }
        }
      case Some(_) =>
        roomRepo.isBanned(roomId, userId).flatMap {
          case true => Future.successful(Left("You are banned from this room"))
          case false =>
            roomRepo.isMember(roomId, userId).flatMap {
              case true  => Future.successful(Right(()))
              case false => roomRepo.addMember(roomId, userId, MemberRole.Member).map(Right(_))
            }
        }
    }

  def leave(roomId: Long, userId: Long): Future[Either[String, Unit]] =
    roomRepo.findById(roomId).flatMap {
      case None => Future.successful(Left("Room not found"))
      case Some(room) if room.ownerId == userId =>
        Future.successful(Left("Transfer ownership before leaving"))
      case Some(_) =>
        roomRepo.removeMember(roomId, userId).map(Right(_))
    }

  def listPublic(search: Option[String]): Future[Seq[Room]] =
    roomRepo.findPublic(search)

  def listForMember(userId: Long): Future[Seq[Room]] =
    roomRepo.findByMember(userId)

  def getMembers(roomId: Long): Future[Seq[RoomMember]] =
    roomRepo.findMembers(roomId)

  def listBans(roomId: Long): Future[Seq[RoomBan]] =
    roomRepo.findBans(roomId)

  def ban(roomId: Long, targetUserId: Long, requesterId: Long): Future[Either[String, Unit]] =
    roomRepo.getMemberRole(roomId, requesterId).flatMap {
      case Some(MemberRole.Owner) | Some(MemberRole.Admin) =>
        roomRepo.getMemberRole(roomId, targetUserId).flatMap {
          case Some(MemberRole.Owner) => Future.successful(Left("Cannot ban the room owner"))
          case _ =>
            roomRepo.ban(roomId, targetUserId, requesterId)
              .flatMap(_ => roomRepo.removeMember(roomId, targetUserId))
              .map(Right(_))
        }
      case _ =>
        Future.successful(Left("Insufficient permissions"))
    }

  def unban(roomId: Long, targetUserId: Long, requesterId: Long): Future[Either[String, Unit]] =
    roomRepo.getMemberRole(roomId, requesterId).flatMap {
      case Some(MemberRole.Owner) | Some(MemberRole.Admin) =>
        roomRepo.unban(roomId, targetUserId).map(Right(_))
      case _ =>
        Future.successful(Left("Insufficient permissions"))
    }

  def removeMember(roomId: Long, targetUserId: Long, requesterId: Long): Future[Either[String, Unit]] =
    roomRepo.getMemberRole(roomId, requesterId).flatMap {
      case Some(MemberRole.Owner) =>
        if (targetUserId == requesterId) Future.successful(Left("Cannot remove yourself as owner"))
        else roomRepo.removeMember(roomId, targetUserId).map(Right(_))
      case Some(MemberRole.Admin) =>
        roomRepo.getMemberRole(roomId, targetUserId).flatMap {
          case Some(MemberRole.Owner) => Future.successful(Left("Admins cannot remove the owner"))
          case Some(MemberRole.Admin) => Future.successful(Left("Admins cannot remove other admins"))
          case _ => roomRepo.removeMember(roomId, targetUserId).map(Right(_))
        }
      case _ =>
        Future.successful(Left("Insufficient permissions"))
    }

  def changeRole(roomId: Long, targetUserId: Long, newRole: MemberRole, requesterId: Long): Future[Either[String, Unit]] =
    roomRepo.getMemberRole(roomId, requesterId).flatMap {
      case Some(MemberRole.Owner) =>
        if (targetUserId == requesterId && newRole != MemberRole.Owner) Future.successful(Left("Cannot demote yourself"))
        else roomRepo.updateRole(roomId, targetUserId, newRole).map(Right(_))
      case Some(MemberRole.Admin) =>
        if (newRole == MemberRole.Owner) Future.successful(Left("Admins cannot promote to owner"))
        else roomRepo.getMemberRole(roomId, targetUserId).flatMap {
          case Some(MemberRole.Owner) => Future.successful(Left("Admins cannot change owner's role"))
          case Some(MemberRole.Admin) => Future.successful(Left("Admins cannot change other admins' roles"))
          case _ => roomRepo.updateRole(roomId, targetUserId, newRole).map(Right(_))
        }
      case _ =>
        Future.successful(Left("Insufficient permissions"))
    }

  def invite(roomId: Long, targetUsername: String, requesterId: Long): Future[Either[String, Unit]] =
    roomRepo.findById(roomId).flatMap {
      case None => Future.successful(Left("Room not found"))
      case Some(_) =>
        roomRepo.getMemberRole(roomId, requesterId).flatMap {
          case None => Future.successful(Left("You are not a member of this room"))
          case Some(_) =>
            userRepo.findByEmailOrUsername(targetUsername).flatMap {
              case None => Future.successful(Left("User not found"))
              case Some(target) =>
                roomRepo.isBanned(roomId, target.id).flatMap {
                  case true => Future.successful(Left("User is banned from this room"))
                  case false =>
                    roomRepo.isMember(roomId, target.id).flatMap {
                      case true => Future.successful(Left("User is already a member"))
                      case false =>
                        invitationRepo.findPending(roomId, target.id).flatMap {
                          case Some(_) => Future.successful(Left("Invitation already pending"))
                          case None =>
                            invitationRepo.create(roomId, requesterId, target.id).flatMap { inv =>
                              userRepo.findById(requesterId).map { inviterOpt =>
                                registry.broadcastToUser(target.id, Json.obj(
                                  "type"       -> "room_invitation",
                                  "roomId"     -> roomId,
                                  "invitedBy"  -> Json.toJson(inviterOpt.map(_.username).getOrElse(requesterId.toString)),
                                ))
                                Right(())
                              }
                            }
                        }
                    }
                }
            }
        }
    }

  def listInvitations(roomId: Long): Future[Seq[repositories.RoomInvitation]] =
    invitationRepo.findByRoom(roomId)

  def myInvitations(userId: Long): Future[Seq[repositories.RoomInvitation]] =
    invitationRepo.findPendingForUser(userId)
}
