package api

import api.base.Config
import api.session.Sessions

import neo._
import neo.{Env => NeoEnv}

import redis.{Env => RedisEnv}

import kafka.{Env => KafkaEnv}

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

    val neo = NeoEnv(
        dbPath = config.neoPath,
        logger = LoggerFactory.getLogger("Neo"),
        executionContext = system.dispatchers.lookup("neo.dispatcher")
    )

    val redis = RedisEnv(
        host = config.redisHost,
        port = config.redisPort,
        password = config.redisPassword,
        logger = LoggerFactory.getLogger("Redis"),
        system = ActorSystem("redis")
    )

    val kafka = KafkaEnv(
        zkConnect = config.zkConnect,
        brokers = config.kafkaBrokers,
        logger = LoggerFactory.getLogger("Kafka")
    )

    import api.base.NeoModel._
    val init = for {
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.User}) ASSERT n.${Prop.UserForeignId} IS UNIQUE")
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.User}) ASSERT n.${Prop.UserName} IS UNIQUE")
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.Block}) ASSERT n.${Prop.BlockId} IS UNIQUE")
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.User}) ASSERT n.${Prop.UserId} IS UNIQUE")
    } yield ()
    val runInit =
        neo.run(init)
            .map(_ => neo.logger.info("Database initialized"))
            .recover {
                case e: Throwable => neo.logger.error(s"Database initialization failed: ${e.getMessage}")
            }
    Await.ready(runInit, Duration.Inf)

    lifecycle.addStopHook { () =>
        for {
            _ <- neo.shutdown()
            _ <- redis.shutdown()
            _ <- kafka.shutdown()
        } yield ()
    }
}
