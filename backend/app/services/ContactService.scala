package services

import models.{Conversation, Friendship}
import play.api.libs.json.Json
import pubsub.ActorRegistry
import repositories.{ConversationRepository, DmConversationRepository, FriendshipRepository, UserRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class FriendInfo(
  userId: Long,
  friendshipId: Long,
  status: String,
  conversationId: Option[Long],
)

@Singleton
class ContactService @Inject() (
  friendRepo: FriendshipRepository,
  convRepo: ConversationRepository,
  dmRepo: DmConversationRepository,
  userRepo: UserRepository,
  registry: ActorRegistry,
)(implicit ec: ExecutionContext) {

  def sendRequest(requesterId: Long, addresseeId: Long, message: Option[String] = None): Future[Either[String, Friendship]] = {
    friendRepo.findBetween(requesterId, addresseeId).flatMap {
      case Some(_) => Future.successful(Left("Friend request already exists"))
      case None =>
        for {
          banA   <- friendRepo.isBanned(requesterId, addresseeId)
          banB   <- friendRepo.isBanned(addresseeId, requesterId)
          result <- if (banA || banB)
            Future.successful(Left("Cannot send request to this user"))
          else
            friendRepo.create(requesterId, addresseeId, message.map(_.trim).filter(_.nonEmpty)).flatMap { f =>
              userRepo.findById(requesterId).map { requesterOpt =>
                val event = Json.obj(
                  "type"        -> "friend_request",
                  "friendshipId"-> f.id,
                  "message"     -> f.message,
                  "from"        -> Json.obj(
                    "id"          -> requesterId,
                    "username"    -> requesterOpt.map(_.username),
                    "displayName" -> requesterOpt.flatMap(_.displayName),
                  ),
                )
                registry.broadcastToUser(addresseeId, event)
                Right(f)
              }
            }
        } yield result
    }
  }

  def respond(friendshipId: Long, addresseeId: Long, accept: Boolean): Future[Either[String, Friendship]] =
    friendRepo.findById(friendshipId).flatMap {
      case None => Future.successful(Left("Friend request not found"))
      case Some(f) if f.addresseeId != addresseeId => Future.successful(Left("Not authorized"))
      case Some(f) if f.status != "pending" => Future.successful(Left("Request already responded"))
      case Some(f) =>
        val newStatus = if (accept) "accepted" else "rejected"
        friendRepo.updateStatus(friendshipId, newStatus).flatMap { _ =>
          if (accept) {
            dmRepo.findByUsers(f.requesterId, f.addresseeId).flatMap {
              case Some(_) => Future.unit
              case None =>
                convRepo.createForDm().flatMap { conv =>
                  dmRepo.create(conv.id, f.requesterId, f.addresseeId).map { _ =>
                    val event = Json.obj(
                      "type" -> "friend_request_accepted",
                      "friendId" -> addresseeId,
                      "conversationId" -> conv.id,
                    )
                    registry.broadcastToUser(f.requesterId, event)
                  }
                }
            }
          } else Future.unit
        }.flatMap(_ => friendRepo.findById(friendshipId).map(_.toRight("Not found")))
    }

  def block(blockerId: Long, blockedId: Long): Future[Either[String, Unit]] =
    friendRepo.addBan(blockerId, blockedId).flatMap { _ =>
      dmRepo.findByUsers(blockerId, blockedId).flatMap {
        case Some(convId) => dmRepo.freeze(convId)
        case None         => Future.unit
      }
    }.map(_ => Right(()))

  def unblock(blockerId: Long, blockedId: Long): Future[Unit] =
    friendRepo.removeBan(blockerId, blockedId)

  def remove(requesterId: Long, contactId: Long): Future[Either[String, Unit]] =
    friendRepo.findBetween(requesterId, contactId).flatMap {
      case None    => Future.successful(Left("Not in contacts"))
      case Some(_) =>
        friendRepo.delete(requesterId, contactId).flatMap { _ =>
          dmRepo.findByUsers(requesterId, contactId).flatMap {
            case Some(convId) => dmRepo.freeze(convId).map(_ => Right(()))
            case None         => Future.successful(Right(()))
          }
        }
    }

  def listFriends(userId: Long): Future[Seq[FriendInfo]] =
    friendRepo.findAcceptedForUser(userId).flatMap { friends =>
      Future.sequence(friends.map { f =>
        val otherId = if (f.requesterId == userId) f.addresseeId else f.requesterId
        dmRepo.findByUsers(userId, otherId).map { convId =>
          FriendInfo(otherId, f.id, f.status, convId)
        }
      })
    }

  def pendingRequests(userId: Long): Future[Seq[Friendship]] =
    friendRepo.findPendingForUser(userId)
}
