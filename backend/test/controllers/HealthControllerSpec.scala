package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.test.Helpers._
import play.api.test._

class HealthControllerSpec extends PlaySpec with GuiceOneAppPerTest {
  "HealthController GET /api/health" should {
    "return 200 with status ok" in {
      val request = FakeRequest(GET, "/api/health")
      val result  = route(app, request).get
      status(result) mustBe OK
      contentAsString(result) must include("ok")
    }
  }
}
