package api.session

import api._
import api.base._

import model.base._

import neo._

import org.mindrot.jbcrypt.BCrypt

import play.api.mvc._

import scalaz._
import Scalaz._

class SessionController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    import api.base.NeoModel._

    import global.executionContext

    def login = Actions.public { (timestamp, body) =>
        val name = (body \ "name").as[String]
        val provided = (body \ "password").as[String]
        val prg = for {
            result <- Query.lift { db =>
                val userName = Prop.UserName =:= name
                val checkedUserId = for {
                    node <- Option(db.findNode(Label.User, userName.name, userName.value))
                    id <- Prop.UserId.from(node).toOption
                    stored <- Prop.UserPassword.from(node).toOption
                    avatar = Prop.UserAvatar.from(node).toOption
                } yield if (BCrypt.checkpw(provided, stored)) (id, avatar)
                        else throw ApiError(401, "Authentication failed")
                checkedUserId.getOrElse(throw ApiError(401, "User not found"))
            }.program
            (userId, avatar) = result
            token <- Sessions.start(userId, global.config.sessionExpire).program
        } yield model.session.Session(userId, token, name, avatar)
        Program.run(prg, global.env)
    }

    def logout = Actions.authenticated(parse.empty) { (userId, timestamp, _) =>
        val prg = Sessions.remove(userId).program
        Program.run(prg, global.env)
    }
}
