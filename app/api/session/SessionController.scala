package api.session

import api._
import api.base.Actions
import api.base.ApiError

import model.base._

import neo._

import org.mindrot.jbcrypt.BCrypt

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

import scala.collection.JavaConversions._

class SessionController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    import api.base.NeoModel._

    def login = Actions.public { (timestamp, body) =>
        val name = (body \ "name").as[String]
        val provided = (body \ "password").as[String]
        val getUserId = Query.lift { db =>
            val checkedUserId = for {
                node <- Option(db.findNode(Label.User, Prop.UserName.name, NeoValue(name).underlying))
                userId <- Prop.UserId from node
                stored <- Prop.UserPassword from node
            } yield
                if (BCrypt.checkpw(provided, stored)) userId
                else throw ApiError(401, "Authentication failed")
            checkedUserId.getOrElse(throw ApiError(401, "User not found"))
        }

        for {
            userId <- global.neo.run(getUserId)
            startSession = Sessions.start(userId, global.config.sessionExpire)
            token <- global.redis.run(startSession)
        } yield model.session.Session(userId, token, name)
    }

    def logout = Actions.authenticated(parse.empty) { (userId, timestamp, _) =>
        val remove = Sessions.remove(userId)
        global.redis.run(remove)
    }
}
