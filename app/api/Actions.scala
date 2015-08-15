package api

import model.UserId

import neo.NeoQuery

import play.api.mvc._
import play.api.mvc.BodyParsers._
import play.api.mvc.Results._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future

object Actions {
    def authenticated[T: Writes](fn: (UserId, Long, JsValue) => NeoQuery.Exec[T])(implicit global: Global) =
        Action.async(parse.json) { request =>
            val token = request.queryString.get("token").flatMap(_.headOption)
            token match {
                case None => Future.successful(Unauthorized)
                case Some(t) =>
                    val userId = Sessions.getUserByToken(t)
                    val action = public(fn(userId, _, _))
                    action(request)
            }
        }

    def public[T: Writes](fn: (Long, JsValue) => NeoQuery.Exec[T])(implicit global: Global) =
        Action.async(parse.json) { request =>
            val timestamp = System.currentTimeMillis
            val exec = fn(timestamp, request.body)
            val result = global.neo.run(exec)(
                success = v => Json.obj("data" -> v),
                failure = e => Json.obj("error" -> e.getMessage)
            )
            result.map(json => Ok(json.toString))
        }
}
