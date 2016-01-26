package api

import org.neo4j.graphdb.GraphDatabaseService

import scalaz._
import Scalaz._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import scala.concurrent.duration.Duration

package object base {
    def validate[T](validation: ValidationNel[Throwable, T]): T =
        validation.fold(
            fail = es => throw ApiError(500, es.list.map(_.getMessage).mkString(", \n")),
            succ = t => t
        )

    type Err[T] = Throwable \/ T
    type Program[T] = Kleisli[Err, Env, T]

    object Program {
        def lift[T](value: => T): Program[T] = value.point[Program]

        val noop: Program[Unit] = lift(())

        def run[T](program: Program[T], env: Env): Future[T] = {
            val transactional = neo.Query.transaction(program.local[GraphDatabaseService](Env(_, env.redis, env.kafka, env.http)))
            transactional.program(env).fold(
                l = (e) => Future.failed(e),
                r = (res) => Future.successful(res)
            )
        }
    }

    implicit class FromNeoQuery[T](query: neo.Query.Exec[T]) {
        def program: Program[T] = query.local(_.db)
    }

    implicit class FromKafka[T](command: kafka.Command.Exec[T]) {
        def program: Program[T] = command.local(_.kafka)
    }

    implicit class FromRedisCommand[T](command: redis.Command.Exec[T]) {
        def program(implicit ec: ExecutionContext): Program[T] =
            command.mapK[Err, T](f => Await.result(f.map(_.right).recover { case t => t.left }, Duration.Inf)).local(_.redis)
    }

    implicit class FromHttpClient[T](command: http.Command.Exec[T]) {
        def program(implicit ec: ExecutionContext): Program[T] =
            command.mapK[Err, T](f => Await.result(f.map(_.right).recover { case t => t.left }, Duration.Inf)).local(_.http)
    }
}
