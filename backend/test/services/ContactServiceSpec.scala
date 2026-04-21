package services

import models.Friendship
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import pubsub.ActorRegistry
import repositories.{ConversationRepository, DmConversationRepository, FriendshipRepository, UserRepository}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ContactServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar {

  private def friendship(id: Long, requesterId: Long, addresseeId: Long, status: String) =
    Friendship(id, requesterId, addresseeId, status, None, Instant.now(), Instant.now())

  private def makeService(
    friendRepo: FriendshipRepository = mock[FriendshipRepository],
    convRepo: ConversationRepository = mock[ConversationRepository],
    dmRepo: DmConversationRepository = mock[DmConversationRepository],
    userRepo: UserRepository = mock[UserRepository],
    registry: ActorRegistry = mock[ActorRegistry],
  ) = new ContactService(friendRepo, convRepo, dmRepo, userRepo, registry)

  "ContactService" should {

    "send friend request creates pending friendship" in {
      val friendRepo = mock[FriendshipRepository]
      val dmRepo     = mock[DmConversationRepository]
      val userRepo   = mock[UserRepository]
      val registry   = mock[ActorRegistry]

      val pending = friendship(1L, 10L, 20L, "pending")
      when(friendRepo.findBetween(10L, 20L)).thenReturn(Future.successful(None))
      when(friendRepo.isBanned(10L, 20L)).thenReturn(Future.successful(false))
      when(friendRepo.isBanned(20L, 10L)).thenReturn(Future.successful(false))
      when(friendRepo.create(10L, 20L)).thenReturn(Future.successful(pending))
      when(userRepo.findById(10L)).thenReturn(Future.successful(None))
      doNothing().when(registry).broadcastToUser(any(), any())

      val service = makeService(friendRepo = friendRepo, dmRepo = dmRepo, userRepo = userRepo, registry = registry)
      service.sendRequest(10L, 20L).futureValue mustBe Right(pending)
    }

    "send friend request returns Left when already exists" in {
      val friendRepo = mock[FriendshipRepository]
      when(friendRepo.findBetween(10L, 20L)).thenReturn(Future.successful(Some(friendship(1L, 10L, 20L, "pending"))))

      val service = makeService(friendRepo = friendRepo)
      service.sendRequest(10L, 20L).futureValue mustBe Left("Friend request already exists")
    }

    "accept friend request creates DM conversation" in {
      val friendRepo = mock[FriendshipRepository]
      val convRepo   = mock[ConversationRepository]
      val dmRepo     = mock[DmConversationRepository]
      val registry   = mock[ActorRegistry]

      val pending  = friendship(1L, 10L, 20L, "pending")
      val accepted = friendship(1L, 10L, 20L, "accepted")
      when(friendRepo.findById(1L))
        .thenReturn(Future.successful(Some(pending)))
        .thenReturn(Future.successful(Some(accepted)))
      when(friendRepo.updateStatus(1L, "accepted")).thenReturn(Future.successful(()))
      when(dmRepo.findByUsers(10L, 20L)).thenReturn(Future.successful(None))
      when(convRepo.createForDm()).thenReturn(Future.successful(models.Conversation(99L, None, "dm", Instant.now())))
      when(dmRepo.create(99L, 10L, 20L)).thenReturn(Future.successful(()))
      doNothing().when(registry).broadcastToUser(any(), any())

      val service = makeService(friendRepo = friendRepo, convRepo = convRepo, dmRepo = dmRepo, registry = registry)
      service.respond(1L, addresseeId = 20L, accept = true).futureValue mustBe a[Right[_, _]]
    }

    "reject friend request sets status to rejected" in {
      val friendRepo = mock[FriendshipRepository]
      val convRepo   = mock[ConversationRepository]
      val dmRepo     = mock[DmConversationRepository]
      val registry   = mock[ActorRegistry]

      val pending  = friendship(2L, 10L, 30L, "pending")
      val rejected = friendship(2L, 10L, 30L, "rejected")
      when(friendRepo.findById(2L))
        .thenReturn(Future.successful(Some(pending)))
        .thenReturn(Future.successful(Some(rejected)))
      when(friendRepo.updateStatus(2L, "rejected")).thenReturn(Future.successful(()))

      val service = makeService(friendRepo = friendRepo, convRepo = convRepo, dmRepo = dmRepo, registry = registry)
      service.respond(2L, addresseeId = 30L, accept = false).futureValue mustBe a[Right[_, _]]
    }

    "block user adds ban row" in {
      val friendRepo = mock[FriendshipRepository]
      val dmRepo     = mock[DmConversationRepository]

      when(friendRepo.addBan(10L, 20L)).thenReturn(Future.successful(()))
      when(dmRepo.findByUsers(10L, 20L)).thenReturn(Future.successful(None))

      val service = makeService(friendRepo = friendRepo, dmRepo = dmRepo)
      service.block(10L, 20L).futureValue mustBe Right(())
    }

    "block user freezes existing DM conversation" in {
      val friendRepo = mock[FriendshipRepository]
      val dmRepo     = mock[DmConversationRepository]

      when(friendRepo.addBan(10L, 20L)).thenReturn(Future.successful(()))
      when(dmRepo.findByUsers(10L, 20L)).thenReturn(Future.successful(Some(55L)))
      when(dmRepo.freeze(55L)).thenReturn(Future.successful(()))

      val service = makeService(friendRepo = friendRepo, dmRepo = dmRepo)
      service.block(10L, 20L).futureValue mustBe Right(())
      verify(dmRepo).freeze(55L)
    }

    "remove contact deletes friendship and freezes DM" in {
      val friendRepo = mock[FriendshipRepository]
      val dmRepo     = mock[DmConversationRepository]

      when(friendRepo.findBetween(10L, 20L)).thenReturn(Future.successful(Some(friendship(1L, 10L, 20L, "accepted"))))
      when(friendRepo.delete(10L, 20L)).thenReturn(Future.successful(()))
      when(dmRepo.findByUsers(10L, 20L)).thenReturn(Future.successful(Some(42L)))
      when(dmRepo.freeze(42L)).thenReturn(Future.successful(()))

      val service = makeService(friendRepo = friendRepo, dmRepo = dmRepo)
      service.remove(10L, 20L).futureValue mustBe Right(())
      verify(dmRepo).freeze(42L)
    }
  }
}
