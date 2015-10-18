package api.base

import api.session.Sessions

import model.base.UserId

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Sink

import play.api.mvc._
import play.api.mvc.BodyParsers._
import play.api.mvc.Results._
import play.api.libs.iteratee._
import play.api.libs.json._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Actions {
    type Public[R, T] = (Long, R) => Future[T]

    def authenticated[T: Writes](fn: (UserId, Long, JsValue) => Future[T])(implicit ec: ExecutionContext, global: api.Global): Action[JsValue] =
        authenticated(parse.json)(fn)

    def authenticated[R, T: Writes](bp: BodyParser[R])(fn: (UserId, Long, R) => Future[T])(implicit ec: ExecutionContext, global: api.Global): Action[R] =
        Action.async(bp) { request =>
            val failure = Future.successful {
                renderError(ApiError(401, "You must be logged in for this operation"))
            }
            def success(userId: UserId) = {
                val publicFn: Public[R, T] = fn(userId, _, _)
                val action = public(bp)(publicFn)
                action(request)
            }
            authenticate(request)(success, failure).recover {
                case e: Throwable => Actions.renderError(e)
            }
        }

    def public[T: Writes](fn: (Long, JsValue) => Future[T])(implicit ec: ExecutionContext): Action[JsValue] =
        public(parse.json)(fn)

    def public[R, T: Writes](bp: BodyParser[R])(fn: (Long, R) => Future[T])(implicit ec: ExecutionContext): Action[R] =
        Action.async(bp) { request =>
            val timestamp = System.currentTimeMillis
            fn(timestamp, request.body)
                .map(v => Actions.envelope(v))
                .recover {
                    case e: Throwable => Actions.renderError(e)
                }
        }

    def authenticatedSocket[T: Writes](fn: UserId => Future[Source[T, _]])(implicit ec: ExecutionContext, mat: Materializer, global: api.Global): WebSocket[String, String] =
        WebSocket.using[String] { request =>
            val failure = Future.successful((Iteratee.ignore[String], Enumerator.eof[String]))
            def success(userId: UserId) = socket(fn(userId))
            val result = authenticate(request)(success, failure)
            Await.result(result, Duration.Inf)
        }

    def publicSocket[T: Writes](fn: => Future[Source[T, _]])(implicit ec: ExecutionContext, mat: Materializer): WebSocket[String, String] =
        WebSocket.using[String] { _ =>
            val result = socket(fn)
            Await.result(result, Duration.Inf)
        }

    private def socket[T: Writes](fn: => Future[Source[T, _]])(implicit ec: ExecutionContext, mat: Materializer): Future[(Iteratee[String, _], Enumerator[String])] = {
        val (out, channel) = Concurrent.broadcast[String]
        fn.map { source =>
            source.to(Sink.foreach { msg =>
                val frame = Json.toJson(msg).toString
                channel.push(frame)
            }).run()
            val in = Iteratee.foreach[String] {
                case "PING" => channel.push("PONG")
                case _ =>
            }
            (in, out)
        } recover {
            case e: Throwable =>
                channel.push(e.getMessage)
                (Iteratee.ignore[String], out)
        }
    }

    private def authenticate[T](request: RequestHeader)(success: UserId => Future[T], failure: => Future[T])(implicit ec: ExecutionContext, global: api.Global): Future[T] = {
        val token = request.queryString.get("token").flatMap(_.headOption)
        token match {
            case None =>
                failure
            case Some(t) =>
                val getSession = Sessions.get(t).program
                val userId = Program.run(getSession, global.env)
                userId.flatMap(success)
        }
    }

    private def envelope[T: Writes](value: T) =
        Ok(Json.obj("data" -> value).toString).as("application/json")

    private def renderError(e: Throwable) = {
        val body = Json.obj("error" -> e.getMessage)
        val code = e match {
            case ApiError(c, _) => c
            case _ => 500
        }
        new Status(code)(body.toString).as("application/json")
    }
}
