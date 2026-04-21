package controllers

import java.time.Instant

import filters.{AuthenticatedAction, UserRequest}
import models.{Conversation, User}
import org.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._
import play.api.test._
import pubsub.ActorRegistry
import repositories.{ConversationRepository, DmConversationRepository, FriendshipRepository, UserRepository, UserSessionRepository}
import services.{ContactService, JwtService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ContactControllerSpec extends PlaySpec with MockitoSugar {
  private def fakeAuth(userId: Long, sessionId: String = "test-session"): AuthenticatedAction = {
    val parser = stubControllerComponents().parsers.default
    new AuthenticatedAction(parser, mock[JwtService], mock[UserRepository], mock[UserSessionRepository]) {
      override def invokeBlock[A](request: play.api.mvc.Request[A], block: UserRequest[A] => Future[play.api.mvc.Result]): Future[play.api.mvc.Result] =
        block(new UserRequest(userId, sessionId, request))
    }
  }

  private def makeContactService(
    friendRepo: FriendshipRepository = mock[FriendshipRepository],
    convRepo: ConversationRepository = mock[ConversationRepository],
    dmRepo: DmConversationRepository = mock[DmConversationRepository],
    userRepo: UserRepository = mock[UserRepository],
    registry: ActorRegistry = mock[ActorRegistry],
  ): ContactService = new ContactService(friendRepo, convRepo, dmRepo, userRepo, registry)

  "ContactController#getDmConversation" should {
    "reject creating a DM with self" in {
      val userRepo = mock[UserRepository]
      val convRepo = mock[ConversationRepository]
      val dmRepo   = mock[DmConversationRepository]
      val friendRepo = mock[FriendshipRepository]
      val controller = new ContactController(
        stubControllerComponents(), fakeAuth(1), makeContactService(convRepo = convRepo, dmRepo = dmRepo),
        userRepo, convRepo, dmRepo, friendRepo,
      )

      val result = controller.getDmConversation(1).apply(FakeRequest())
      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("Cannot DM yourself")
    }

    "return not found when target user does not exist" in {
      val userRepo = mock[UserRepository]
      val convRepo = mock[ConversationRepository]
      val dmRepo   = mock[DmConversationRepository]
      val friendRepo = mock[FriendshipRepository]
      when(userRepo.findById(2L)).thenReturn(Future.successful(None))

      val controller = new ContactController(
        stubControllerComponents(), fakeAuth(1), makeContactService(convRepo = convRepo, dmRepo = dmRepo),
        userRepo, convRepo, dmRepo, friendRepo,
      )
      val result = controller.getDmConversation(2).apply(FakeRequest())
      status(result) mustBe NOT_FOUND
      contentAsString(result) must include("User not found")
    }

    "create a new DM conversation when none exists" in {
      val userRepo = mock[UserRepository]
      val convRepo = mock[ConversationRepository]
      val dmRepo   = mock[DmConversationRepository]
      val friendRepo = mock[FriendshipRepository]

      when(userRepo.findById(2L)).thenReturn(Future.successful(Some(User(2L, "other@example.com", "other", "hash", None, None, false, Instant.now()))))
      when(dmRepo.findByUsers(1L, 2L)).thenReturn(Future.successful(None))
      when(convRepo.createForDm()).thenReturn(Future.successful(Conversation(5L, None, "dm", Instant.now())))
      when(dmRepo.create(5L, 1L, 2L)).thenReturn(Future.successful(()))
      when(friendRepo.findBetween(1L, 2L)).thenReturn(Future.successful(Some(models.Friendship(0L, 1L, 2L, "accepted", None, Instant.now(), Instant.now()))))

      val controller = new ContactController(
        stubControllerComponents(), fakeAuth(1), makeContactService(convRepo = convRepo, dmRepo = dmRepo),
        userRepo, convRepo, dmRepo, friendRepo,
      )
      val result = controller.getDmConversation(2).apply(FakeRequest())
      status(result) mustBe OK
      contentAsString(result) must include("\"conversationId\":5")
    }
  }
}
