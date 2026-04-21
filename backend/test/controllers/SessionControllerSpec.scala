package controllers

import java.time.Instant
import java.time.temporal.ChronoUnit
import models.UserSession
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test._
import repositories.UserSessionRepository

import scala.concurrent.Future

class SessionControllerSpec extends PlaySpec with GuiceOneAppPerSuite {
  implicit val ec: scala.concurrent.ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  "SessionController GET /api/sessions" should {
    "return list of sessions with isCurrent flag" in {
      val sessionRepo = app.injector.instanceOf[UserSessionRepository]
      val userId = 1L
      
      val now = Instant.now()
      val session1 = UserSession(
        0, userId, "session1-hash", Some("Mozilla/5.0"), Some("192.168.1.1"),
        now.minus(1, ChronoUnit.DAYS), now, now.plus(72, ChronoUnit.HOURS)
      )
      val session2 = UserSession(
        0, userId, "session2-hash", Some("Chrome"), Some("192.168.1.2"),
        now, now, now.plus(72, ChronoUnit.HOURS)
      )

      // Create sessions
      for {
        _ <- sessionRepo.create(session1)
        _ <- sessionRepo.create(session2)
        
        // Fetch and verify
        sessions <- sessionRepo.findByUserId(userId)
      } yield {
        sessions.length must be >= 2
        val session1Found = sessions.find(_.tokenHash == "session1-hash")
        val session2Found = sessions.find(_.tokenHash == "session2-hash")
        session1Found must not be empty
        session2Found must not be empty
      }
    }
  }

  "SessionController DELETE /api/sessions/:sessionId" should {
    "delete a session" in {
      val sessionRepo = app.injector.instanceOf[UserSessionRepository]
      val userId = 2L
      
      val now = Instant.now()
      val session = UserSession(
        0, userId, "to-delete-hash", None, None,
        now, now, now.plus(72, ChronoUnit.HOURS)
      )

      for {
        _ <- sessionRepo.create(session)
        sessionsBefore <- sessionRepo.findByUserId(userId)
        _ <- sessionRepo.delete("to-delete-hash")
        sessionsAfter <- sessionRepo.findByUserId(userId)
      } yield {
        val beforeCount = sessionsBefore.count(_.tokenHash == "to-delete-hash")
        val afterCount = sessionsAfter.count(_.tokenHash == "to-delete-hash")
        beforeCount must be > 0
        afterCount mustBe 0
      }
    }
  }

  "SessionController" should {
    "delete only expired sessions" in {
      val sessionRepo = app.injector.instanceOf[UserSessionRepository]
      val userId = 3L
      
      val now = Instant.now()
      val expiredSession = UserSession(
        0, userId, "expired-hash", None, None,
        now.minus(10, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS), 
        now.minus(1, ChronoUnit.HOURS) // expired
      )
      val validSession = UserSession(
        0, userId, "valid-hash", None, None,
        now, now, now.plus(72, ChronoUnit.HOURS) // not expired
      )

      for {
        _ <- sessionRepo.create(expiredSession)
        _ <- sessionRepo.create(validSession)
        sessionsBefore <- sessionRepo.findByUserId(userId)
        deletedCount <- sessionRepo.deleteExpired()
        sessionsAfter <- sessionRepo.findByUserId(userId)
      } yield {
        sessionsBefore.length mustBe 2
        deletedCount must be > 0
        sessionsAfter.length mustBe 1
        sessionsAfter.find(_.tokenHash == "valid-hash") must not be empty
      }
    }
  }

  "SessionController" should {
    "delete all sessions for a user" in {
      val sessionRepo = app.injector.instanceOf[UserSessionRepository]
      val userId = 4L
      
      val now = Instant.now()
      val session1 = UserSession(
        0, userId, "user4-session1", None, None,
        now, now, now.plus(72, ChronoUnit.HOURS)
      )
      val session2 = UserSession(
        0, userId, "user4-session2", None, None,
        now, now, now.plus(72, ChronoUnit.HOURS)
      )

      for {
        _ <- sessionRepo.create(session1)
        _ <- sessionRepo.create(session2)
        sessionsBefore <- sessionRepo.findByUserId(userId)
        deletedCount <- sessionRepo.deleteAllForUser(userId)
        sessionsAfter <- sessionRepo.findByUserId(userId)
      } yield {
        sessionsBefore.length mustBe 2
        deletedCount mustBe 2
        sessionsAfter.length mustBe 0
      }
    }
  }
}
