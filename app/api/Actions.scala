package api

import model.UserId

import neo.Query

import play.api.mvc._
import play.api.mvc.BodyParsers._
import play.api.mvc.Results._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._

import scalaz._

import scala.concurrent.Future

object Actions {
    def authenticated[T: Writes](fn: (UserId, Long, JsValue) => Query.Exec[T])(implicit global: Global) =
        Action.async(parse.json) { request =>
            val token = request.queryString.get("token").flatMap(_.headOption)
            val publicFn: Future[(Long, JsValue) => Query.Exec[T]] = token match {
                case None =>
                    Future.successful {
                        (_, _) => Query.error(ApiException(401, "You must be logged in for this operation"))
                    }
                case Some(t) =>
                    global.sessions.get(t).run.map {
                        case \/-(userId) => fn(userId, _, _)
                        case -\/(e) => (_, _) => Query.error(e)
                    }
            }
            for {
                fn <- publicFn
                action = public(fn)
                result <- action(request)
            } yield result
        }

    def public[T: Writes](fn: (Long, JsValue) => Query.Exec[T])(implicit global: Global) =
        Action.async(parse.json) { request =>
            val timestamp = System.currentTimeMillis
            val exec = fn(timestamp, request.body)
            global.neo.run(exec).run.map {
                case -\/(e) => renderError(e)
                case \/-(v) => Ok(Json.obj("data" -> v).toString)
            }
        }

    def renderError(e: Throwable) = {
        val body = Json.obj("error" -> e.getMessage)
        val code = e match {
            case ApiException(c, _) => c
            case _ => 500
        }
        new Status(code)(body.toString)
    }
}
