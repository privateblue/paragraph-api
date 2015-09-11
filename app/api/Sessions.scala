package api

import model.UserId

import redis.RedisClient

import akka.actor.ActorSystem

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

case class Sessions(config: Config)(implicit system: ActorSystem) {
    private val redis = RedisClient(config.redisHost, config.redisPort, config.redisPassword)

    def get(token: String)(implicit ec: ExecutionContext): Future[UserId] =
        redis.get[UserId](token).map {
            case Some(id) => id
            case _ => throw ApiError(401, "User not found")
        }

    def start(userId: UserId, expire: Long = 0)(implicit ec: ExecutionContext): Future[String] = {
        redis.get[String](userId.toString).flatMap {
            case Some(token) =>
                get(token)
                    .map(_ => token)
                    .recoverWith {
                        case ApiError(_, _) => for {
                            _ <- remove(token)
                            newToken <- generate(userId, expire)
                        } yield newToken
                    }
            case _ =>
                generate(userId, expire)
        }
    }

    def generate(userId: UserId, expire: Long = 0)(implicit ec: ExecutionContext): Future[String] = {
        val token = IdGenerator.key
        for {
            s1 <- redis.set[String](userId.toString, token, if (expire == 0) None else Some(expire))
            s2 <- redis.set[UserId](token, userId, if (expire == 0) None else Some(expire))
        } yield
            if (s1 && s2) token
            else throw ApiError(503, "Unable to create session")
    }

    def remove(token: String)(implicit ec: ExecutionContext): Future[Unit] = for {
        userId <- redis.get[UserId](token)
        _ <- userId.map(id => redis.del(id.toString)).getOrElse(Future.successful(()))
        _ <- redis.del(token)
    } yield ()
}
