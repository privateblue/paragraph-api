package api.session

import api.base.Config
import api.base.ApiError
import api.base.IdGenerator

import model.base.UserId

import redis.Command

import scalaz._
import Scalaz._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

object Sessions {
    def get(token: String)(implicit ec: ExecutionContext) = Command { redis =>
        redis.get[UserId](token).map {
            case Some(id) => id
            case _ => throw ApiError(401, "User not found")
        }
    }

    def start(userId: UserId, expire: Long = 0)(implicit ec: ExecutionContext) = for {
        getToken <- Command { redis =>
            (for {
                token <- OptionT[Future, String](redis.get[String](userId.toString))
                _ <- OptionT[Future, UserId](redis.get[UserId](token))
            } yield token).run
        }
        token <- getToken.map(_.point[Command.Exec]).getOrElse(for {
            _ <- remove(userId)
            newToken <- generate(userId, expire)
        } yield newToken)
    } yield token

    def generate(userId: UserId, expire: Long = 0)(implicit ec: ExecutionContext) = Command { redis =>
        val token = IdGenerator.key
        for {
            s1 <- redis.set[String](userId.toString, token, if (expire == 0) None else Some(expire))
            s2 <- redis.set[UserId](token, userId, if (expire == 0) None else Some(expire))
        } yield
            if (s1 && s2) token
            else throw ApiError(503, "Unable to create session")
    }

    def remove(userId: UserId)(implicit ec: ExecutionContext) = Command { redis =>
        for {
            token <- redis.get[String](userId.toString)
            _ <- redis.del(userId.toString)
            _ <- token.map(t => redis.del(t)).getOrElse(Future.successful(()))
        } yield ()
    }
}
