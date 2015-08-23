package api

import model.UserId

import neo.Query

import play.api.mvc._
import play.api.mvc.BodyParsers._
import play.api.mvc.Results._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._

object Reader {
    def authenticated[T: Writes](fn: UserId => Query.Exec[T])(implicit global: Global) =
        Actions.authenticate(parse.empty) { (request, userId) =>
            val exec = fn(userId)
            val action = public(exec)
            action(request)
        }

    def public[T: Writes](exec: => Query.Exec[T])(implicit global: Global) =
        Action.async(parse.empty) { request =>
            global.neo.run(exec)
                .map(v => Ok(Json.obj("data" -> v).toString))
                .recover {
                    case e: Throwable => Actions.renderError(e)
                }
        }
}
