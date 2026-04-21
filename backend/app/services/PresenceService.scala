package services

import models.PresenceStatus
import play.api.libs.json.Json
import pubsub.{ActorRegistry, RedisPublisher}
import repositories.PresenceRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PresenceService @Inject() (
  registry: ActorRegistry,
  presenceRepo: PresenceRepository,
  redisPublisher: RedisPublisher,
)(implicit ec: ExecutionContext) {

  def computeAndBroadcast(userId: Long): Future[Unit] = {
    val status = registry.derivePresence(userId)
    presenceRepo.findStatus(userId).flatMap {
      case Some(existing) if existing == status => Future.successful(())
      case _ => presenceRepo.upsertStatus(userId, status)
    }.map { _ =>
      val event = Json.obj(
        "type" -> "presence_update",
        "userId" -> userId,
        "status" -> PresenceStatus.toString(status),
        "_recipients" -> Json.arr(),
      )
      redisPublisher.publish(redisPublisher.presenceChannel(userId), event)
    }.recover {
      case _: java.util.concurrent.RejectedExecutionException => () // normal during shutdown
      case ex => play.api.Logger("PresenceService").error(s"computeAndBroadcast failed for user $userId", ex)
    }
  }
}
