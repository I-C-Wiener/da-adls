package services

import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import repositories.{AttachmentRepository, MessageRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MessageExpirationServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar {

  "MessageExpirationService" should {

    "call deleteExpired on the repository and return count" in {
      val repo       = mock[MessageRepository]
      val attRepo    = mock[AttachmentRepository]
      val config     = Configuration("chat.messageExpirationDays" -> 30)
      val svc        = new MessageExpirationService(repo, attRepo, config)

      when(repo.findExpiredIds()).thenReturn(Future.successful(Seq.empty[Long]))
      when(attRepo.findByMessageIds(Seq.empty)).thenReturn(Future.successful(Seq.empty))
      when(repo.deleteExpired()).thenReturn(Future.successful(5))

      svc.deleteExpired().futureValue mustBe 5
      verify(repo, times(1)).deleteExpired()
    }

    "report expirationDays=0 when configured to never expire" in {
      val repo    = mock[MessageRepository]
      val attRepo = mock[AttachmentRepository]
      val config  = Configuration("chat.messageExpirationDays" -> 0)
      val svc     = new MessageExpirationService(repo, attRepo, config)
      svc.expirationDays mustBe 0
    }
  }
}
