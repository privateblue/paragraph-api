package api

import model.UserId

import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._

import scalaz._

import scala.concurrent.Future

object Actions {
    def renderError(e: Throwable) = {
        val body = Json.obj("error" -> e.getMessage)
        val code = e match {
            case ApiError(c, _) => c
            case _ => 500
        }
        new Status(code)(body.toString)
    }

    def authenticate[T](bp: BodyParser[T])(fn: (Request[T], UserId) => Future[Result])(implicit global: Global) =
        Action.async(bp) { request =>
            val token = request.queryString.get("token").flatMap(_.headOption)
            token match {
                case None => Future.successful {
                    renderError(ApiError(401, "You must be logged in for this operation"))
                }
                case Some(t) => global.sessions.get(t).run.flatMap {
                    case -\/(e) => Future.successful {
                        renderError(e)
                    }
                    case \/-(userId) => fn(request, userId)
                }
            }
        }
}
