import com.google.inject.{AbstractModule, Provides}
import filters.AuthenticatedAction
import jobs.MessageExpirationJob
import play.api.mvc.{AnyContent, BodyParser, BodyParsers}
import pubsub.RedisSubscriber
import services._

class Module extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[RedisSubscriber]).asEagerSingleton()
    bind(classOf[MessageExpirationJob]).asEagerSingleton()
    bind(classOf[JwtService]).asEagerSingleton()
    bind(classOf[AuthenticatedAction]).asEagerSingleton()
  }

  @Provides
  def provideBodyParser(defaultBodyParser: BodyParsers.Default): BodyParser[AnyContent] = defaultBodyParser
}
