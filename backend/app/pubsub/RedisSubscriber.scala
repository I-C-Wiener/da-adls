package pubsub

import io.lettuce.core.RedisClient
import io.lettuce.core.pubsub.{RedisPubSubAdapter, StatefulRedisPubSubConnection}
import play.api.Configuration
import play.api.libs.json.Json

import javax.inject.{Inject, Singleton}

@Singleton
class RedisSubscriber @Inject() (
  config: Configuration,
  registry: ActorRegistry,
) {
  private val client: RedisClient =
    RedisClient.create(s"redis://${config.get[String]("redis.host")}:${config.get[Int]("redis.port")}")
  private val connection: StatefulRedisPubSubConnection[String, String] = client.connectPubSub()

  connection.addListener(new RedisPubSubAdapter[String, String] {
    override def message(channel: String, message: String): Unit = {
      val json = Json.parse(message)
      val userIds = (json \ "_recipients").asOpt[Seq[Long]].getOrElse(Seq.empty)
      if (userIds.nonEmpty) registry.broadcastToRoom(userIds, json)
      else registry.broadcastToAll(json)
    }
  })

  connection.async().psubscribe("presence:*")

  def subscribe(channels: String*): Unit =
    connection.async().subscribe(channels: _*)

  def unsubscribe(channels: String*): Unit =
    connection.async().unsubscribe(channels: _*)
}
