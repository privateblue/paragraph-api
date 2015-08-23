package api

import model.UserId

import redis.RedisClient

import akka.actor.ActorSystem

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

case class Sessions(config: Config)(implicit system: ActorSystem) {
    private val redis = RedisClient(config.redisHost, config.redisPort, config.redisPassword)

    def get(token: String)(implicit ec: ExecutionContext): Future[UserId] =
        redis.get[String](token).map {
            case Some(t) => UserId(t)
            case _ => throw ApiError(401, "Token not found")
        }

    def create(userId: UserId, expire: Long = 0)(implicit ec: ExecutionContext): Future[String] = {
        val token = java.util.UUID.randomUUID.toString
        redis.set[String](token, userId.key, if (expire == 0) None else Some(expire)).map { success =>
            if (success) token
            else throw ApiError(503, "Unable to create session")
        }
    }

    def delete(token: String)(implicit ec: ExecutionContext): Future[Unit] =
        redis.del(token).map { n =>
            if (n > 0) ()
            else throw ApiError(500, "Token not found")
        }
}
