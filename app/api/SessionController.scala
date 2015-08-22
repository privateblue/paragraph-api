package api

import model._

import neo._

import play.api.mvc._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._

import scalaz._
import scalaz.std.scalaFuture._

import scala.collection.JavaConversions._
import scala.concurrent.Future

class SessionController @javax.inject.Inject() (implicit global: Global) extends Controller {
    import NeoModel._

    def login = Action.async(parse.json) { request =>
        val name = (request.body \ "name").as[String]
        val provided = (request.body \ "password").as[String]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserName + name}}) RETURN a.${Prop.UserPassword}, a.${Prop.UserId}"""
        val getUserId = Query.result(query) { result =>
            if (result.hasNext) {
                val record = result.next()
                val userId = UserId(record(s"a.${Prop.UserId.name}").asInstanceOf[String])
                val stored = record(s"a.${Prop.UserPassword.name}").asInstanceOf[String]
                if (stored == provided) userId
                else throw ApiException(401, "Authentication failed")
            } else throw ApiException(401, "User not found")
        }
        val getToken = for {
            userId <- global.neo.run(getUserId)
            token <- global.sessions.create(userId, global.config.sessionExpire)
        } yield token
        getToken.run.map {
            case -\/(e) => Actions.renderError(e)
            case \/-(token) => Ok(Json.obj("data" -> token).toString)
        }
    }
}
