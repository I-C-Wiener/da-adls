package services

import models._
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import repositories.MessageRepository

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MessageServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar {

  private val config30 = Configuration("chat.messageExpirationDays" -> 30)
  private val config0  = Configuration("chat.messageExpirationDays" -> 0)

  private def makeMessage(id: Long, conversationId: Long, senderId: Long) =
    Message(id, conversationId, senderId, Some("hello"), None, isEdited = false, None, isDeleted = false, Instant.now(), None)

  "MessageService" should {

    "save message and set expires_at when expirationDays > 0" in {
      val repo = mock[MessageRepository]
      val captor = ArgumentCaptor.forClass(classOf[Message])
      when(repo.insert(any())).thenAnswer(inv => Future.successful(inv.getArgument[Message](0).copy(id = 1L)))

      val svc = new MessageService(repo, config30)
      val result = svc.save(42L, 1L, "hello", None).futureValue

      verify(repo).insert(captor.capture())
      captor.getValue.expiresAt must not be empty
      result.content mustBe Some("hello")
    }

    "save message with no expires_at when expirationDays = 0" in {
      val repo = mock[MessageRepository]
      when(repo.insert(any())).thenAnswer(inv => Future.successful(inv.getArgument[Message](0).copy(id = 1L)))

      val svc = new MessageService(repo, config0)
      svc.save(42L, 1L, "hello", None).futureValue

      val captor = ArgumentCaptor.forClass(classOf[Message])
      verify(repo).insert(captor.capture())
      captor.getValue.expiresAt mustBe empty
    }

    "edit message by sender" in {
      val repo = mock[MessageRepository]
      val msg  = makeMessage(1L, 42L, senderId = 10L)
      when(repo.findById(1L)).thenReturn(Future.successful(Some(msg)))
      when(repo.update(any())).thenAnswer(inv => Future.successful(inv.getArgument[Message](0)))

      val svc    = new MessageService(repo, config30)
      val result = svc.edit(1L, "updated", requesterId = 10L).futureValue

      result mustBe a[Right[_, _]]
      result.toOption.get.content mustBe Some("updated")
      result.toOption.get.isEdited mustBe true
    }

    "reject edit by non-sender" in {
      val repo = mock[MessageRepository]
      val msg  = makeMessage(1L, 42L, senderId = 10L)
      when(repo.findById(1L)).thenReturn(Future.successful(Some(msg)))

      val svc    = new MessageService(repo, config30)
      val result = svc.edit(1L, "updated", requesterId = 99L).futureValue

      result mustBe Left("Not allowed")
    }

    "soft delete by sender" in {
      val repo = mock[MessageRepository]
      when(repo.softDelete(1L, 10L)).thenReturn(Future.successful(1))

      val svc    = new MessageService(repo, config30)
      val result = svc.delete(1L, requesterId = 10L).futureValue

      result mustBe Right(())
    }

    "paginate messages" in {
      val repo = mock[MessageRepository]
      val msgs = (1 to 3).map(i => makeMessage(i.toLong, 42L, 1L)).toSeq
      when(repo.findPage(42L, None, 50)).thenReturn(Future.successful(msgs))

      val svc    = new MessageService(repo, config30)
      val result = svc.paginate(42L, None, 50).futureValue

      result must have size 3
    }
  }
}
