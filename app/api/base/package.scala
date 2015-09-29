package api

import scalaz._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

package object base {
    type AsyncErr[T] = EitherT[Future, Throwable, T]
    type Program[T] = Kleisli[AsyncErr, Env, T]

    object Program {
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

    implicit class FromKafkaOut[T](out: kafka.Command.Out[T]) {
        def program(implicit ec: ExecutionContext): Program[T] =
            out.mapK[AsyncErr, T](f => EitherT(f.map(\/.right))).local(_.kafkaProducer)
    }

    implicit class FromKafkaIn[T](in: kafka.Command.In[T]) {
        def program(implicit ec: ExecutionContext): Program[T] =
            in.mapK[AsyncErr, T](v => EitherT(Future.successful(v))).local(_.kafkaConsumerConfig)
    }
}
