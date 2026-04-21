package jobs

import akka.actor.ActorSystem
import play.api.{Configuration, Logging}
import services.MessageExpirationService

import java.time.{Instant, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class MessageExpirationJob @Inject() (
  actorSystem: ActorSystem,
  expirationService: MessageExpirationService,
  config: Configuration,
)(implicit ec: ExecutionContext) extends Logging {

  if (expirationService.expirationDays > 0) {
    actorSystem.scheduler.scheduleAtFixedRate(
      initialDelay = delayUntilMidnightUtc(),
      interval = 24.hours,
    ) { () =>
      expirationService.deleteExpired().foreach { count =>
        logger.info(s"MessageExpirationJob: purged $count messages")
      }
    }
    logger.info(s"MessageExpirationJob scheduled (expirationDays=${expirationService.expirationDays})")
  } else {
    logger.info("MessageExpirationJob disabled (MESSAGE_EXPIRATION_DAYS=0)")
  }

  private def delayUntilMidnightUtc(): FiniteDuration = {
    val now      = Instant.now().atZone(ZoneOffset.UTC)
    val midnight = now.toLocalDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant
    val millis   = midnight.toEpochMilli - Instant.now().toEpochMilli
    millis.millis
  }
}
