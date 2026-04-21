package services

import models.UserSession
import play.api.Logging
import repositories.{AttachmentRepository, ConversationRepository, PasswordResetTokenRepository, RoomRepository, UserRepository, UserSessionRepository}

import java.nio.file.{Files, Paths}
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.{Base64, UUID}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthService @Inject() (
  userRepo: UserRepository,
  sessionRepo: UserSessionRepository,
  jwtService: JwtService,
  roomRepo: RoomRepository,
  convRepo: ConversationRepository,
  attachmentRepo: AttachmentRepository,
  resetTokenRepo: PasswordResetTokenRepository,
)(implicit ec: ExecutionContext) extends Logging {

  private val expiryHours = 72 // from config, but hardcoded for now

  def register(email: String, username: String, password: String, userAgent: Option[String] = None, ip: String = ""): Future[Either[String, String]] = {
    val hash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt(12))
    userRepo.create(email.toLowerCase.trim, username.trim, hash).map {
      case Right(user) =>
        val sessionId = UUID.randomUUID().toString
        val expiresAt = Instant.now().plus(expiryHours, ChronoUnit.HOURS)
        val session = UserSession(0, user.id, sessionId, userAgent, Some(ip).filter(_.nonEmpty), Instant.now(), Instant.now(), expiresAt)
        sessionRepo.create(session).map { _ =>
          Right(jwtService.generateToken(user.id, sessionId, user.username))
        }
      case Left(err)   => Future.successful(Left(err))
    }.flatten
  }

  def login(loginId: String, password: String, userAgent: Option[String], ip: String): Future[Either[String, String]] = {
    userRepo.findByEmailOrUsername(loginId.toLowerCase.trim).flatMap {
      case Some(user) if !user.isDeleted && org.mindrot.jbcrypt.BCrypt.checkpw(password, user.passwordHash) =>
        val sessionId = UUID.randomUUID().toString
        val expiresAt = Instant.now().plus(expiryHours, ChronoUnit.HOURS)
        val session = UserSession(0, user.id, sessionId, userAgent, Some(ip), Instant.now(), Instant.now(), expiresAt)
        sessionRepo.create(session).map { _ =>
          Right(jwtService.generateToken(user.id, sessionId, user.username))
        }
      case _ =>
        Future.successful(Left("invalid"))
    }
  }

  def logout(token: String): Future[Unit] =
    jwtService.validateToken(token) match {
      case Some((_, sessionId)) => sessionRepo.delete(sessionId).map(_ => ())
      case None => Future.successful(())
    }

  def changePassword(userId: Long, oldPassword: String, newPassword: String): Future[Either[String, Unit]] =
    userRepo.findById(userId).flatMap {
      case None => Future.successful(Left("User not found"))
      case Some(user) if !org.mindrot.jbcrypt.BCrypt.checkpw(oldPassword, user.passwordHash) =>
        Future.successful(Left("Current password is incorrect"))
      case Some(_) =>
        val newHash = org.mindrot.jbcrypt.BCrypt.hashpw(newPassword, org.mindrot.jbcrypt.BCrypt.gensalt(12))
        userRepo.updatePassword(userId, newHash).map(_ => Right(()))
    }

  def deleteAccount(userId: Long): Future[Unit] = {
    for {
      ownedRooms <- roomRepo.findByOwner(userId)
      _          <- Future.sequence(ownedRooms.map { r =>
                      convRepo.findByRoom(r.id).flatMap { convOpt =>
                        val cleanup = convOpt match {
                          case Some(conv) =>
                            attachmentRepo.findStoredPathsByConversation(conv.id).map { paths =>
                              paths.foreach(p => try { Files.deleteIfExists(Paths.get(p)) } catch { case _: Exception => () })
                            }
                          case None => Future.unit
                        }
                        cleanup.flatMap(_ => roomRepo.hardDelete(r.id))
                      }
                    })
      _          <- roomRepo.removeAllMembershipsForUser(userId)
      _          <- sessionRepo.deleteAllForUser(userId)
      _          <- userRepo.softDelete(userId)
    } yield ()
  }

  def initiatePasswordReset(email: String): Future[Option[String]] =
    userRepo.findByEmail(email.toLowerCase.trim).flatMap {
      case None =>
        Future.successful(None)
      case Some(user) =>
        val rawToken  = UUID.randomUUID().toString.replace("-", "") + UUID.randomUUID().toString.replace("-", "")
        val tokenHash = sha256(rawToken)
        val expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
        resetTokenRepo.create(user.id, tokenHash, expiresAt).map { _ =>
          logger.info(s"[PASSWORD RESET] token for ${user.email}: $rawToken")
          Some(rawToken)
        }
    }

  def resetPassword(rawToken: String, newPassword: String): Future[Either[String, Unit]] = {
    val tokenHash = sha256(rawToken)
    resetTokenRepo.findValid(tokenHash).flatMap {
      case None => Future.successful(Left("Invalid or expired reset token"))
      case Some(userId) =>
        val newHash = org.mindrot.jbcrypt.BCrypt.hashpw(newPassword, org.mindrot.jbcrypt.BCrypt.gensalt(12))
        for {
          _ <- userRepo.updatePassword(userId, newHash)
          _ <- resetTokenRepo.markUsed(tokenHash)
          _ <- sessionRepo.deleteAllForUser(userId)
        } yield Right(())
    }
  }

  private def sha256(input: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    Base64.getEncoder.encodeToString(digest.digest(input.getBytes("UTF-8")))
  }
}
