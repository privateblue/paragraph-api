package api

import neo.Env
import neo.NeoQuery

import org.slf4j.LoggerFactory

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem

import play.api.inject.ApplicationLifecycle

import scalaz._
import Scalaz._

import scala.concurrent.Future

@javax.inject.Singleton
class Global @javax.inject.Inject() (lifecycle: ApplicationLifecycle) {
    val system = ActorSystem()

    val config = Config(ConfigFactory.load())

    val logger = LoggerFactory.getLogger("Neo")

    val neo = Env(
        dbPath = config.neoPath,
        logger = logger,
        executionContext = system.dispatchers.lookup("neo-dispatcher")
    )

    import model.NeoModel._
    val init = for {
        _ <- NeoQuery.exec(s"CREATE CONSTRAINT ON (n:${Labels.User.name}) ASSERT n.${Keys.UserForeignId} IS UNIQUE")(_ => ().right)
        _ <- NeoQuery.exec(s"CREATE CONSTRAINT ON (n:${Labels.User.name}) ASSERT n.${Keys.UserName} IS UNIQUE")(_ => ().right)
        _ <- NeoQuery.exec(s"CREATE CONSTRAINT ON (n:${Labels.Block.name}) ASSERT n.${Keys.BlockId} IS UNIQUE")(_ => ().right)
    } yield ()
    neo.run(init)(
        success = _ => logger.info("Database initialized"),
        failure = e => logger.error(s"Database initialization failed: ${e.getMessage}")
    )

    lifecycle.addStopHook { () =>
        neo.shutdown()
    }
}
