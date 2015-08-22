package api

import model.UserId

import redis.RedisClient

import akka.actor.ActorSystem

import scalaz._
import Scalaz._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

case class Sessions(config: Config)(implicit system: ActorSystem) {
    private val redis = RedisClient(config.redisHost, config.redisPort, config.redisPassword)

    def get(token: String)(implicit ec: ExecutionContext): EitherT[Future, Throwable, UserId] = EitherT {
        redis.get[String](token).map {
            case Some(t) => UserId(t).right
            case _ => ApiException(401, "Token not found").left
        }.recoverWith(PartialFunction(e => Future.successful(e.left)))
    }

    def create(userId: UserId, expire: Long = 0)(implicit ec: ExecutionContext): EitherT[Future, Throwable, String] = EitherT {
        val token = java.util.UUID.randomUUID.toString
        redis.set[String](token, userId.key, if (expire == 0) None else Some(expire)).map { success =>
            if (success) token.right
            else ApiException(503, "Unable to create session").left
        }
    }
}
