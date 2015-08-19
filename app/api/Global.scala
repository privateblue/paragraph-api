package api

import neo._

import org.slf4j.LoggerFactory

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem

import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future

@javax.inject.Singleton
class Global @javax.inject.Inject() (lifecycle: ApplicationLifecycle) {
    implicit val system = ActorSystem()

    val config = Config(ConfigFactory.load())

    val logger = LoggerFactory.getLogger("Neo")

    val sessions = Sessions(config)

    val neo = Env(
        dbPath = config.neoPath,
        logger = logger,
        executionContext = system.dispatchers.lookup("neo-dispatcher")
    )

    import NeoModel._
    val init = for {
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.User}) ASSERT n.${Prop.UserForeignId} IS UNIQUE")
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.User}) ASSERT n.${Prop.UserName} IS UNIQUE")
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.Block}) ASSERT n.${Prop.BlockId} IS UNIQUE")
    } yield ()
    neo.run(init)(
        success = _ => logger.info("Database initialized"),
        failure = e => logger.error(s"Database initialization failed: ${e.getMessage}")
    )

    lifecycle.addStopHook { () =>
        neo.shutdown()
    }
}
