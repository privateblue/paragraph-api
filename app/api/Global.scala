package api

import api.base._
import api.session.Sessions

import neo._

import org.slf4j.LoggerFactory

import com.typesafe.config.ConfigFactory

import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.logging.slf4j.Slf4jLogProvider

import redis.RedisClient

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringSerializer

import akka.actor.ActorSystem

import play.api.libs.concurrent.Execution.Implicits._
import play.api.inject.ApplicationLifecycle

import scalaz.std.scalaFuture._

import scala.collection.JavaConversions._

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration

@javax.inject.Singleton
class Global @javax.inject.Inject() (lifecycle: ApplicationLifecycle) {
    val config = Config(ConfigFactory.load())

    val logger = LoggerFactory.getLogger("Paragraph")

    private val db =
        new GraphDatabaseFactory()
            .setUserLogProvider(new Slf4jLogProvider)
            .newEmbeddedDatabase(config.neoPath)

    private val redisSystem = ActorSystem("Redis")
    private val redis = RedisClient(
        config.redisHost,
        config.redisPort,
        config.redisPassword
    )(redisSystem)

    private val producerConfig = {
        val props = new java.util.Properties
        props.setProperty("bootstrap.servers", config.kafkaBrokers)
        props
    }

    private val producer = new KafkaProducer[String, String](producerConfig, new StringSerializer, new StringSerializer)

    private val consumerConfig = {
        val props = new java.util.Properties
        props.setProperty("zookeeper.connect", config.zkConnect)
        props
    }

    val env = Env(db, redis, producer, consumerConfig)

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
            _ <- Future { redisSystem.shutdown() }
            _ <- Future { producer.close() }
        } yield ()
    }
}
