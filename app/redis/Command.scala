package redis

import scalaz._

import scala.concurrent.Future

object Command {
    type Exec[T] = ReaderT[Future, RedisClient, T]

    def apply[T](fn: RedisClient => Future[T]): Exec[T] =
        Kleisli[Future, RedisClient, T](fn)
}
