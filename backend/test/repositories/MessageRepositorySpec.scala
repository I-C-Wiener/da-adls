package repositories

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.db.slick.DatabaseConfigProvider
import slick.basic.{BasicProfile, DatabaseConfig}
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class MessageRepositorySpec extends AnyWordSpec with Matchers with ScalaFutures {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = 15.seconds)

  private val dbConfig = DatabaseConfig.forConfig[JdbcProfile]("", com.typesafe.config.ConfigFactory.parseString(
    """profile = "slick.jdbc.PostgresProfile$"
      |db.url = "jdbc:postgresql://localhost:5432/chatdb"
      |db.user = "chat"
      |db.password = "chat_secret"
      |db.driver = "org.postgresql.Driver"
      |db.numThreads = 2
      |db.maxConnections = 2
      |""".stripMargin
  ))

  private val provider = new DatabaseConfigProvider {
    override def get[P <: BasicProfile]: DatabaseConfig[P] = dbConfig.asInstanceOf[DatabaseConfig[P]]
  }

  private val repo = new MessageRepository(provider)

  private lazy val db: Database = dbConfig.db.asInstanceOf[Database]

  private def seedConvAndUser(): (Long, Long) = {
    val convId = db.run(sql"SELECT id FROM conversations LIMIT 1".as[Long].head).futureValue
    val userId = db.run(sql"SELECT id FROM users LIMIT 1".as[Long].head).futureValue
    (convId, userId)
  }

  "MessageRepository" should {

    "insert message with no expiresAt" in {
      val (cId, uId) = seedConvAndUser()
      val msg   = models.Message(0L, cId, uId, Some("no-expire-msg"), None, isEdited = false, None, isDeleted = false, Instant.now(), None)
      val saved = repo.insert(msg).futureValue
      try {
        saved.id must be > 0L
        saved.expiresAt mustBe None
      } finally {
        db.run(sqlu"DELETE FROM messages WHERE id = ${saved.id}").futureValue
      }
    }

    "deleteExpired removes rows with expires_at in the past" in {
      val (cId, uId) = seedConvAndUser()
      val past = Instant.now().minusSeconds(3600)
      val msg  = models.Message(0L, cId, uId, Some("expired-msg"), None, isEdited = false, None, isDeleted = false, Instant.now(), Some(past))
      val saved = repo.insert(msg).futureValue

      val count = repo.deleteExpired().futureValue
      count must be >= 1

      db.run(sql"SELECT count(*) FROM messages WHERE id = ${saved.id}".as[Long].head).futureValue mustBe 0L
    }

    "deleteExpired keeps row with expires_at in the future" in {
      val (cId, uId) = seedConvAndUser()
      val future = Instant.now().plusSeconds(86400)
      val msg    = models.Message(0L, cId, uId, Some("future-expire-msg"), None, isEdited = false, None, isDeleted = false, Instant.now(), Some(future))
      val saved  = repo.insert(msg).futureValue

      repo.deleteExpired().futureValue

      val count = db.run(sql"SELECT count(*) FROM messages WHERE id = ${saved.id}".as[Long].head).futureValue
      db.run(sqlu"DELETE FROM messages WHERE id = ${saved.id}").futureValue
      count mustBe 1L
    }

    "deleteExpired returns 0 when no expired rows exist" in {
      db.run(sqlu"DELETE FROM messages WHERE expires_at IS NOT NULL AND expires_at < NOW()").futureValue
      repo.deleteExpired().futureValue mustBe 0
    }
  }
}
