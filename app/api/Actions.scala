package api

import model.UserId

import neo.NeoQuery

import play.api.mvc._
import play.api.mvc.BodyParsers._
import play.api.mvc.Results._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._

import scalaz._
import Scalaz._

import scala.concurrent.Future

object Actions {
    def authenticated[T: Writes](fn: (UserId, Long, JsValue) => NeoQuery.Exec[T])(implicit global: Global) =
        Action.async(parse.json) { request =>
            val token = request.queryString.get("token").flatMap(_.headOption)
            val publicFn: (Long, JsValue) => NeoQuery.Exec[T] = token match {
                case None =>
                    (_, _) => NeoQuery.constant(ApiException(403, "You must be logged in for this operation").left)
                case Some(t) =>
                    val userId = Sessions.getUserByToken(t)
                    fn(userId, _, _)
            }
            val action = public(publicFn)
            action(request)
        }

    def public[T: Writes](fn: (Long, JsValue) => NeoQuery.Exec[T])(implicit global: Global) =
        Action.async(parse.json) { request =>
            val timestamp = System.currentTimeMillis
            val exec = fn(timestamp, request.body)
            global.neo.run(exec)(
                success = v => Ok(Json.obj("data" -> v).toString),
                failure = renderError(_)
            )
        }

    private def renderError(e: Throwable) = {
        val body = Json.obj("error" -> e.getMessage)
        val code = e match {
            case ApiException(c, _) => c
            case _ => 500
        }
        new Status(code)(body.toString)
    }
}
