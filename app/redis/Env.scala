package redis

import org.slf4j.Logger

import akka.actor.ActorSystem

import scala.concurrent.Future

case class Env(
    host: String,
    port: Int,
    password: Option[String],
    logger: Logger,
    system: ActorSystem) {

    private val redis = RedisClient(host, port, password)(system)

    def run[T](exec: Command.Exec[T]): Future[T] =
        exec(redis).recover {
            case e: Throwable =>
                logger.error(e.getMessage)
                throw e
        } (system.dispatcher)
}
