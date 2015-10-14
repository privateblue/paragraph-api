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
            val token = request.queryString.get("token").flatMap(_.headOption)
            token match {
                case None => Future.successful {
                    renderError(ApiError(401, "You must be logged in for this operation"))
                }
                case Some(t) =>
                    val getSession = Sessions.get(t).program
                    val render = for {
                        userId <- Program.run(getSession, global.env)
                        publicFn: Public[R, T] = fn(userId, _, _)
                        action = public(bp)(publicFn)
                        result <- action(request)
                    } yield result
                    render.recover {
                        case e: Throwable => Actions.renderError(e)
                    }
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

    def socket[T: Writes](fn: => Future[Source[T, _]])(implicit ec: ExecutionContext, mat: Materializer): WebSocket[String, String] =
        WebSocket.using[String] { request =>
            val (out, channel) = Concurrent.broadcast[String]
            val result = fn.map { source =>
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
            Await.result(result, Duration.Inf)
        }

    def envelope[T: Writes](value: T) =
        Ok(Json.obj("data" -> value).toString).as("application/json")

    def renderError(e: Throwable) = {
        val body = Json.obj("error" -> e.getMessage)
        val code = e match {
            case ApiError(c, _) => c
            case _ => 500
        }
        new Status(code)(body.toString).as("application/json")
    }
}
