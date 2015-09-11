package api

import model._

import neo._

import org.mindrot.jbcrypt.BCrypt

import play.api.mvc._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._

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
                if (BCrypt.checkpw(provided, stored)) userId
                else throw ApiError(401, "Authentication failed")
            } else throw ApiError(401, "User not found")
        }

        val getToken = for {
            userId <- global.neo.run(getUserId)
            token <- global.sessions.start(userId, global.config.sessionExpire)
        } yield Ok(Json.obj("data" -> model.Session(userId = userId, token = token, name = name)).toString)

        getToken.recover {
            case e: Throwable => Actions.renderError(e)
        }
    }

    def logout = Action.async(parse.empty) { request =>
        val token = request.queryString.get("token").flatMap(_.headOption)
        val delete = token match {
            case Some(t) => global.sessions.remove(t)
            case _ => Future.failed(ApiError(401, "You must be logged in for this operation"))
        }
        delete
            .map(v => Ok(Json.obj("data" -> v).toString))
            .recover {
                case e: Throwable => Actions.renderError(e)
            }
    }
}
