package services

import akka.actor.ActorRef
import models.PresenceStatus
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import pubsub.ActorRegistry
import repositories.PresenceRepository
import pubsub.RedisPublisher

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PresenceServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar {

  private def makeService(registry: ActorRegistry, repo: PresenceRepository, publisher: RedisPublisher) =
    new PresenceService(registry, repo, publisher)

  "PresenceService" should {
    "derive Online for a single active tab" in {
      val registry  = new ActorRegistry()
      val repo      = mock[PresenceRepository]
      val publisher = mock[RedisPublisher]
      val service   = makeService(registry, repo, publisher)

      registry.register(1L, "s1", ActorRef.noSender)
      when(repo.findStatus(1L)).thenReturn(Future.successful(None))
      when(repo.upsertStatus(1L, PresenceStatus.Online)).thenReturn(Future.successful(()))

      service.computeAndBroadcast(1L).futureValue

      verify(repo).upsertStatus(1L, PresenceStatus.Online)
      verify(publisher).publish(any[String](), any[play.api.libs.json.JsValue]())
    }

    "derive Afk when the only tab is idle" in {
      val registry  = new ActorRegistry()
      val repo      = mock[PresenceRepository]
      val publisher = mock[RedisPublisher]
      val service   = makeService(registry, repo, publisher)

      registry.register(1L, "s1", ActorRef.noSender)
      registry.updatePresence(1L, "s1", activeTab = false)

      when(repo.findStatus(1L)).thenReturn(Future.successful(None))
      when(repo.upsertStatus(1L, PresenceStatus.Afk)).thenReturn(Future.successful(()))

      service.computeAndBroadcast(1L).futureValue

      verify(repo).upsertStatus(1L, PresenceStatus.Afk)
    }

    "derive Offline when no ping is present for 90 seconds" in {
      val registry  = new ActorRegistry()
      val repo      = mock[PresenceRepository]
      val publisher = mock[RedisPublisher]
      val service   = makeService(registry, repo, publisher)

      registry.register(1L, "s1", ActorRef.noSender)
      registry.setSessionState(1L, "s1", Instant.now().minusSeconds(120), activeTab = false)

      when(repo.findStatus(1L)).thenReturn(Future.successful(None))
      when(repo.upsertStatus(1L, PresenceStatus.Offline)).thenReturn(Future.successful(()))

      service.computeAndBroadcast(1L).futureValue

      verify(repo).upsertStatus(1L, PresenceStatus.Offline)
    }

    "derive Online when one active tab and one idle tab are connected" in {
      val registry  = new ActorRegistry()
      val repo      = mock[PresenceRepository]
      val publisher = mock[RedisPublisher]
      val service   = makeService(registry, repo, publisher)

      registry.register(1L, "active", ActorRef.noSender)
      registry.register(1L, "idle", ActorRef.noSender)
      registry.setSessionState(1L, "idle", Instant.now().minusSeconds(120), activeTab = false)

      when(repo.findStatus(1L)).thenReturn(Future.successful(None))
      when(repo.upsertStatus(1L, PresenceStatus.Online)).thenReturn(Future.successful(()))

      service.computeAndBroadcast(1L).futureValue

      verify(repo).upsertStatus(1L, PresenceStatus.Online)
    }

    "derive Afk when all active sessions are idle" in {
      val registry  = new ActorRegistry()
      val repo      = mock[PresenceRepository]
      val publisher = mock[RedisPublisher]
      val service   = makeService(registry, repo, publisher)

      registry.register(1L, "s1", ActorRef.noSender)
      registry.updatePresence(1L, "s1", activeTab = false)

      when(repo.findStatus(1L)).thenReturn(Future.successful(None))
      when(repo.upsertStatus(1L, PresenceStatus.Afk)).thenReturn(Future.successful(()))

      service.computeAndBroadcast(1L).futureValue

      verify(repo).upsertStatus(1L, PresenceStatus.Afk)
    }
  }
}
