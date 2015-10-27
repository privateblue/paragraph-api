package http

import akka.stream.Materializer

import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse

import scalaz._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

object Command {
    type Exec[T] = ReaderT[Future, HttpExt, T]

    def get[T](url: String)(fn: HttpResponse => Future[T])(implicit ec: ExecutionContext, mat: Materializer) =
        Kleisli[Future, HttpExt, T] { http =>
            for {
                response <- http.singleRequest(HttpRequest(uri = url))
                result <- fn(response)
            } yield result
        }
}
