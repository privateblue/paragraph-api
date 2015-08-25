package api

import model.UserId

import neo.Query

import play.api.mvc._
import play.api.mvc.BodyParsers._
import play.api.mvc.Results._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._

object Paragraph {
    def authenticated[T: Writes](fn: (UserId, Long, JsValue) => Query.Exec[T])(implicit global: Global) =
        Actions.authenticate(parse.json) { (request, userId) =>
            val publicFn: (Long, JsValue) => Query.Exec[T] = fn(userId, _, _)
            val action = public(publicFn)
            action(request)
        }

    def public[T: Writes](fn: (Long, JsValue) => Query.Exec[T])(implicit global: Global) =
        Action.async(parse.json) { request =>
            val timestamp = System.currentTimeMillis
            val exec = fn(timestamp, request.body)
            global.neo.run(exec)
                .map(v => Actions.envelope(v))
                .recover {
                    case e: Throwable => Actions.renderError(e)
                }
        }
}
