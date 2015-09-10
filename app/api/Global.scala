package api

import neo._

import org.slf4j.LoggerFactory

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem

import play.api.libs.concurrent.Execution.Implicits._
import play.api.inject.ApplicationLifecycle

import scala.collection.JavaConversions._

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration

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
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.User}) ASSERT n.${Prop.UserId} IS UNIQUE")
    } yield ()
    val runInit =
        neo.run(init)
            .map(_ => logger.info("Database initialized"))
            .recover {
                case e: Throwable => logger.error(s"Database initialization failed: ${e.getMessage}")
            }
    Await.ready(runInit, Duration.Inf)

    val fix = Query.lift { db =>
        val nodes = db.getAllNodes().iterator
        while (nodes.hasNext) {
            val node = nodes.next()
            val rels = node.getRelationships().iterator
            while (rels.hasNext) {
                val rel = rels.next()
                val userId = rel.getProperty("userId").asInstanceOf[String]
                if (userId.size == 36) rel.setProperty("userId", IdGenerator.encode(java.util.UUID.fromString(userId)))
            }
        }
    }
    Await.ready(neo.run(fix), Duration.Inf)

    lifecycle.addStopHook { () =>
        neo.shutdown()
    }
}
