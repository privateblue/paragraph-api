package api

import scalaz._
import Scalaz._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

package object base {
    def validate[T](validation: ValidationNel[Throwable, T]): T =
        validation.fold(
            fail = es => throw ApiError(500, es.list.map(_.getMessage).mkString(", \n")),
            succ = t => t
        )

    type AsyncErr[T] = EitherT[Future, Throwable, T]
    type Program[T] = Kleisli[AsyncErr, Env, T]

    object Program {
        def lift[T](value: => T): Program[T] =
            Kleisli[AsyncErr, Env, T](_ => EitherT(Future.successful(value.right)))

        val noop: Program[Unit] = lift(())

        def run[T](program: Program[T], env: Env)(implicit ec: ExecutionContext): Future[T] =
            program(env).run.flatMap {
                case -\/(e) => Future.failed(e)
                case \/-(res) => Future.successful(res)
            }
    }

    implicit class FromNeoQuery[T](query: neo.Query.Exec[T]) {
        def program(implicit ec: ExecutionContext): Program[T] =
            neo.Query.transaction(query).mapK[AsyncErr, T](v => EitherT(Future(v))).local(_.db)
    }

    implicit class FromRedisCommand[T](command: redis.Command.Exec[T]) {
        def program(implicit ec: ExecutionContext): Program[T] =
            command.mapK[AsyncErr, T](f => EitherT(f.map(\/.right))).local(_.redis)
    }

    implicit class FromKafka[T](command: kafka.Command.Exec[T]) {
        def program: Program[T] =
            command.mapK[AsyncErr, T](v => EitherT(Future.successful(v))).local(_.kafka)
    }

    implicit class FromHttpClient[T](command: http.Command.Exec[T]) {
        def program(implicit ec: ExecutionContext): Program[T] =
            command.mapK[AsyncErr, T](f => EitherT(f.map(\/.right))).local(_.http)
    }
}
