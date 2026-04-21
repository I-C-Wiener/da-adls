package services

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration

class JwtServiceSpec extends AnyWordSpec with Matchers {
  private val config = Configuration(
    "jwt.secret"      -> "test_secret_min_32_chars_padding____",
    "jwt.expiryHours" -> 72,
  )
  private val jwt = new JwtService(config)

  "JwtService" should {
    "generate and validate a token for a userId" in {
      val token  = jwt.generateToken(42L, "test-session-id")
      val result = jwt.validateToken(token)
      result mustBe Some((42L, "test-session-id"))
    }

    "return None for a tampered token" in {
      jwt.validateToken("not.a.valid.token") mustBe None
    }

    "return None for an empty string" in {
      jwt.validateToken("") mustBe None
    }
  }
}
