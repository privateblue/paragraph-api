package api

import api.base._
import api.session.Sessions

import neo._

import org.slf4j.LoggerFactory

import com.typesafe.config.ConfigFactory

import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.logging.slf4j.Slf4jLogProvider

import redis.RedisClient

import com.softwaremill.react.kafka.ReactiveKafka

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import play.api.libs.concurrent.Execution
import play.api.inject.ApplicationLifecycle

import scalaz._
import Scalaz._

import scala.collection.JavaConversions._

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration

@javax.inject.Singleton
class Global @javax.inject.Inject() (lifecycle: ApplicationLifecycle) {
    val config = Config(ConfigFactory.load())

    val logger = LoggerFactory.getLogger("Paragraph")

    implicit val executionContext = Execution.defaultContext

    implicit val system = ActorSystem()

    implicit val materializer = ActorMaterializer()

    private val db =
        new GraphDatabaseFactory()
            .setUserLogProvider(new Slf4jLogProvider)
            .newEmbeddedDatabase(config.neoPath)

    private val redis = RedisClient(
        config.redisHost,
        config.redisPort,
        config.redisPassword
    )

    private val kafka = new ReactiveKafka(
        config.kafkaBrokers,
        config.zkConnect
    )


    val env = Env(db, redis, kafka)

    import api.base.NeoModel._
    val init = for {
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.User}) ASSERT n.${Prop.UserForeignId} IS UNIQUE").program
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.User}) ASSERT n.${Prop.UserName} IS UNIQUE").program
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.Block}) ASSERT n.${Prop.BlockId} IS UNIQUE").program
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.User}) ASSERT n.${Prop.UserId} IS UNIQUE").program
    } yield ()
    val runInit =
        Program.run(init, env)
            .map(_ => logger.info("Database initialized"))
            .recover {
                case e: Throwable => logger.error(s"Database initialization failed: ${e.getMessage}")
            }
    Await.ready(runInit, Duration.Inf)

    lifecycle.addStopHook { () =>
        for {
            _ <- Future { db.shutdown() }
            _ <- Future { system.shutdown() }
        } yield ()
    }
}
