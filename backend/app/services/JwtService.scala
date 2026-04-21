package services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import play.api.Configuration

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.inject.{Inject, Singleton}

@Singleton
class JwtService @Inject() (config: Configuration) {
  private val secret      = config.get[String]("jwt.secret")
  private val expiryHours = config.get[Int]("jwt.expiryHours")
  private val algorithm   = Algorithm.HMAC256(secret)
  private val verifier    = JWT.require(algorithm).withIssuer("chat").build()

  def generateToken(userId: Long, sessionId: String, username: String = ""): String =
    JWT.create()
      .withIssuer("chat")
      .withSubject(userId.toString)
      .withClaim("sessionId", sessionId)
      .withClaim("username", username)
      .withExpiresAt(Date.from(Instant.now().plus(expiryHours, ChronoUnit.HOURS)))
      .sign(algorithm)

  def validateToken(token: String): Option[(Long, String)] =
    try {
      val decoded = verifier.verify(token)
      val userId = decoded.getSubject.toLong
      val sessionId = decoded.getClaim("sessionId").asString()
      Some((userId, sessionId))
    } catch {
      case _: JWTVerificationException => None
      case _: NumberFormatException    => None
    }
}
