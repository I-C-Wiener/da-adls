package pubsub

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import play.api.Configuration
import play.api.libs.json.{JsValue, Json}

import javax.inject.{Inject, Singleton}

@Singleton
class RedisPublisher @Inject() (config: Configuration) {
  private val client: RedisClient =
    RedisClient.create(s"redis://${config.get[String]("redis.host")}:${config.get[Int]("redis.port")}")
  private val connection: StatefulRedisConnection[String, String] = client.connect()
  private val commands = connection.async()

  def publish(channel: String, json: JsValue): Unit =
    commands.publish(channel, Json.stringify(json))

  def roomChannel(roomId: Long): String       = s"room:$roomId"
  def presenceChannel(userId: Long): String   = s"presence:$userId"
  def notifyChannel(userId: Long): String     = s"user:$userId:notifications"
  def dmChannel(a: Long, b: Long): String     = s"dm:${math.min(a,b)}:${math.max(a,b)}"
}
