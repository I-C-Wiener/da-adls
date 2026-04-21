package pubsub

import actors.WebSocketActor
import akka.actor.ActorRef
import models.PresenceStatus
import play.api.libs.json.JsValue

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.{Inject, Singleton}
import scala.jdk.CollectionConverters._

case class TabState(ref: ActorRef, lastPing: Instant, activeTab: Boolean)

@Singleton
class ActorRegistry @Inject() () {
  private type SessionId = String
  private val registry: ConcurrentHashMap[Long, ConcurrentHashMap[SessionId, TabState]] =
    new ConcurrentHashMap()

  def register(userId: Long, sessionId: String, ref: ActorRef): Unit = {
    registry.computeIfAbsent(userId, _ => new ConcurrentHashMap())
    registry.get(userId).put(sessionId, TabState(ref, Instant.now(), activeTab = true))
  }

  def unregister(userId: Long, sessionId: String): Unit = {
    Option(registry.get(userId)).foreach { sessions =>
      sessions.remove(sessionId)
      if (sessions.isEmpty) registry.remove(userId)
    }
  }

  def updatePresence(userId: Long, sessionId: String, activeTab: Boolean): Unit = {
    Option(registry.get(userId)).foreach { sessions =>
      Option(sessions.get(sessionId)).foreach { state =>
        sessions.put(sessionId, state.copy(lastPing = Instant.now(), activeTab = activeTab))
      }
    }
  }

  def derivePresence(userId: Long): PresenceStatus = {
    val threshold = Instant.now().minusSeconds(90)
    Option(registry.get(userId)).map(_.values().asScala.toSeq) match {
      case None | Some(Nil) => PresenceStatus.Offline
      case Some(tabs) =>
        val active = tabs.filter(_.lastPing.isAfter(threshold))
        if (active.isEmpty) PresenceStatus.Offline
        else if (active.exists(_.activeTab)) PresenceStatus.Online
        else PresenceStatus.Afk
    }
  }

  def broadcastToUser(userId: Long, json: JsValue): Unit = {
    Option(registry.get(userId)).foreach { sessions =>
      sessions.values().asScala.foreach(_.ref ! WebSocketActor.Broadcast(json))
    }
  }

  def broadcastToRoom(roomUserIds: Seq[Long], json: JsValue): Unit =
    roomUserIds.foreach(broadcastToUser(_, json))

  def allConnectedUserIds(): Seq[Long] =
    registry.keys().asScala.toSeq

  def broadcastToAll(json: JsValue): Unit =
    registry.values().asScala.flatMap(_.values().asScala).foreach(_.ref ! WebSocketActor.Broadcast(json))

  def kickUser(userId: Long, event: JsValue): Unit =
    broadcastToUser(userId, event)

  def kickBySessionId(userId: Long, sessionId: String): Unit = {
    Option(registry.get(userId)).foreach { sessions =>
      Option(sessions.get(sessionId)).foreach { state =>
        state.ref ! WebSocketActor.Broadcast(
          play.api.libs.json.Json.obj("type" -> "error", "code" -> "SESSION_REVOKED", "message" -> "This session has been logged out remotely")
        )
        state.ref ! akka.actor.PoisonPill
      }
    }
  }

  def setSessionState(userId: Long, sessionId: String, lastPing: Instant, activeTab: Boolean): Unit = {
    Option(registry.get(userId)).foreach { sessions =>
      Option(sessions.get(sessionId)).foreach { state =>
        sessions.put(sessionId, state.copy(lastPing = lastPing, activeTab = activeTab))
      }
    }
  }
}
