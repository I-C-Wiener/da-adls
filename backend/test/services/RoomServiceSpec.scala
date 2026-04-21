package services

import models._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import pubsub.ActorRegistry
import repositories.{AttachmentRepository, ConversationRepository, RoomInvitationRepository, RoomRepository, UserRepository}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RoomServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar {

  private def makeRoom(id: Long, name: String, ownerId: Long) =
    Room(id, name, None, RoomVisibility.Public, ownerId, Instant.now(), None)

  private def makeConversation(id: Long, roomId: Long) =
    Conversation(id, Some(roomId), "room", Instant.now())

  private def makeUser(id: Long) =
    User(id, s"user$id@example.com", s"user$id", "hash", Some("User"), None, false, Instant.now())

  "RoomService" should {

    "create a room and add creator as owner member" in {
      val roomRepo  = mock[RoomRepository]
      val convRepo  = mock[ConversationRepository]
      val userRepo  = mock[UserRepository]
      val room      = makeRoom(1L, "general", 42L)
      val conv      = makeConversation(10L, 1L)

      when(userRepo.findById(42L)).thenReturn(Future.successful(Some(makeUser(42L))))
      when(roomRepo.create(any(), any(), any(), any())).thenReturn(Future.successful(room))
      when(convRepo.createForRoom(1L)).thenReturn(Future.successful(conv))
      when(roomRepo.addMember(1L, 42L, MemberRole.Owner)).thenReturn(Future.successful(()))

      val svc = new RoomService(roomRepo, convRepo, userRepo, mock[RoomInvitationRepository], mock[AttachmentRepository], mock[ActorRegistry])
      val result = svc.create("general", None, RoomVisibility.Public, 42L).futureValue

      result mustBe Right(room)
      verify(roomRepo).addMember(1L, 42L, MemberRole.Owner)
      verify(convRepo).createForRoom(1L)
    }

    "return Left when room name is already taken" in {
      val roomRepo = mock[RoomRepository]
      val convRepo = mock[ConversationRepository]
      val userRepo = mock[UserRepository]

      when(userRepo.findById(1L)).thenReturn(Future.successful(Some(makeUser(1L))))
      when(roomRepo.create(any(), any(), any(), any()))
        .thenReturn(Future.failed(new Exception("unique constraint")))

      val svc    = new RoomService(roomRepo, convRepo, userRepo, mock[RoomInvitationRepository], mock[AttachmentRepository], mock[ActorRegistry])
      val result = svc.create("taken", None, RoomVisibility.Public, 1L).futureValue

      result mustBe a[Left[_, _]]
    }

    "return Left when owner user does not exist" in {
      val roomRepo = mock[RoomRepository]
      val convRepo = mock[ConversationRepository]
      val userRepo = mock[UserRepository]

      when(userRepo.findById(42L)).thenReturn(Future.successful(None))

      val svc    = new RoomService(roomRepo, convRepo, userRepo, mock[RoomInvitationRepository], mock[AttachmentRepository], mock[ActorRegistry])
      val result = svc.create("orphan", None, RoomVisibility.Public, 42L).futureValue

      result mustBe Left("User not found")
      verify(roomRepo, never()).create(any(), any(), any(), any())
    }

    "join public room successfully" in {
      val roomRepo = mock[RoomRepository]
      val convRepo = mock[ConversationRepository]
      val userRepo = mock[UserRepository]
      val room     = makeRoom(1L, "general", 99L)

      when(roomRepo.findById(1L)).thenReturn(Future.successful(Some(room)))
      when(roomRepo.isBanned(1L, 42L)).thenReturn(Future.successful(false))
      when(roomRepo.isMember(1L, 42L)).thenReturn(Future.successful(false))
      when(roomRepo.addMember(1L, 42L, MemberRole.Member)).thenReturn(Future.successful(()))

      val svc    = new RoomService(roomRepo, convRepo, userRepo, mock[RoomInvitationRepository], mock[AttachmentRepository], mock[ActorRegistry])
      val result = svc.join(1L, 42L).futureValue

      result mustBe Right(())
    }

    "refuse join when user is banned" in {
      val roomRepo = mock[RoomRepository]
      val convRepo = mock[ConversationRepository]
      val userRepo = mock[UserRepository]
      val room     = makeRoom(1L, "general", 99L)

      when(roomRepo.findById(1L)).thenReturn(Future.successful(Some(room)))
      when(roomRepo.isBanned(1L, 42L)).thenReturn(Future.successful(true))

      val svc    = new RoomService(roomRepo, convRepo, userRepo, mock[RoomInvitationRepository], mock[AttachmentRepository], mock[ActorRegistry])
      val result = svc.join(1L, 42L).futureValue

      result mustBe Left("You are banned from this room")
    }

    "return Right(()) when user already a member (idempotent join)" in {
      val roomRepo = mock[RoomRepository]
      val convRepo = mock[ConversationRepository]
      val userRepo = mock[UserRepository]
      val room     = makeRoom(1L, "general", 99L)

      when(roomRepo.findById(1L)).thenReturn(Future.successful(Some(room)))
      when(roomRepo.isBanned(1L, 42L)).thenReturn(Future.successful(false))
      when(roomRepo.isMember(1L, 42L)).thenReturn(Future.successful(true))

      val svc    = new RoomService(roomRepo, convRepo, userRepo, mock[RoomInvitationRepository], mock[AttachmentRepository], mock[ActorRegistry])
      val result = svc.join(1L, 42L).futureValue

      result mustBe Right(())
      verify(roomRepo, never()).addMember(any(), any(), any())
    }

    "leave room successfully" in {
      val roomRepo = mock[RoomRepository]
      val convRepo = mock[ConversationRepository]
      val userRepo = mock[UserRepository]
      val room     = makeRoom(1L, "general", 99L)

      when(roomRepo.findById(1L)).thenReturn(Future.successful(Some(room)))
      when(roomRepo.removeMember(1L, 42L)).thenReturn(Future.successful(()))

      val svc    = new RoomService(roomRepo, convRepo, userRepo, mock[RoomInvitationRepository], mock[AttachmentRepository], mock[ActorRegistry])
      val result = svc.leave(1L, 42L).futureValue

      result mustBe Right(())
    }

    "prevent owner from leaving without transferring ownership" in {
      val roomRepo = mock[RoomRepository]
      val convRepo = mock[ConversationRepository]
      val userRepo = mock[UserRepository]
      val room     = makeRoom(1L, "general", ownerId = 42L)

      when(roomRepo.findById(1L)).thenReturn(Future.successful(Some(room)))

      val svc    = new RoomService(roomRepo, convRepo, userRepo, mock[RoomInvitationRepository], mock[AttachmentRepository], mock[ActorRegistry])
      val result = svc.leave(1L, 42L).futureValue

      result mustBe Left("Transfer ownership before leaving")
    }
  }
}
