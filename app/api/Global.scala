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
    } yield ()
    val runInit =
        neo.run(init)
            .map(_ => logger.info("Database initialized"))
            .recover {
                case e: Throwable => logger.error(s"Database initialization failed: ${e.getMessage}")
            }
    Await.ready(runInit, Duration.Inf)

    val oneOff = for {
        _ <- Query.lift { db =>
            val nodes = db.findNodes(Label.Block)
            while(nodes.hasNext) {
                val node = nodes.next()
                val key = node.getProperty(Prop.BlockId.name).asInstanceOf[String]
                val uuid = java.util.UUID.fromString(key)
                val newId = IdGenerator.encode(uuid)
                node.setProperty(Prop.BlockId.name, newId)
                logger.info(s"Changing block id from $key to $newId")
            }
        }

        _ <- Query.lift { db =>
            val nodes = db.findNodes(Label.User)
            while(nodes.hasNext) {
                val node = nodes.next()
                val key = node.getProperty(Prop.UserId.name).asInstanceOf[String]
                val uuid = java.util.UUID.fromString(key)
                val newId = IdGenerator.encode(uuid)
                node.setProperty(Prop.UserId.name, newId)
                logger.info(s"Changing user id from $key to $newId")
            }
        }

        _ <- Query.lift { db =>
            val rels = org.neo4j.tooling.GlobalGraphOperations.at(db).getAllRelationships().iterator
            while(rels.hasNext) {
                val rel = rels.next()
                val key = rel.getProperty(Prop.UserId.name).asInstanceOf[String]
                val uuid = java.util.UUID.fromString(key)
                val newId = IdGenerator.encode(uuid)
                rel.setProperty(Prop.BlockId.name, newId)
                logger.info(s"Changing relationship user id from $key to $newId")
            }
        }
    } yield ()
    val runOneOff =
        neo.run(oneOff)
            .map(_ => logger.info("One-off maintenance task finished"))
            .recover {
                case e: Throwable => logger.error(s"One-off maintenance task failed failed: ${e.getMessage}")
            }
    Await.ready(runOneOff, Duration.Inf)

    lifecycle.addStopHook { () =>
        neo.shutdown()
    }
}
