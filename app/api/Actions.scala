package api

import neo.NeoQuery

import play.api.mvc._
import play.api.mvc.BodyParsers._
import play.api.mvc.Results._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._

object Actions {
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
