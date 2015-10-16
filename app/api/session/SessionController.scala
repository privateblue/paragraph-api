package api.session

import api._
import api.base._

import model.base._

import neo._

import org.mindrot.jbcrypt.BCrypt

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

import scalaz.std.scalaFuture._

import scala.collection.JavaConversions._

class SessionController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    import api.base.NeoModel._

    def login = Actions.public { (timestamp, body) =>
        val name = (body \ "name").as[String]
        val provided = (body \ "password").as[String]
        val prg = for {
            userId <- Query.lift { db =>
                val checkedUserId = for {
                    node <- Option(db.findNode(Label.User, Prop.UserName.name, NeoValue.toNeo(name)))
                    id <- Prop.UserId.from(node).toOption
                    stored <- Prop.UserPassword.from(node).toOption
                } yield if (BCrypt.checkpw(provided, stored)) id
                        else throw ApiError(401, "Authentication failed")
                checkedUserId.getOrElse(throw ApiError(401, "User not found"))
            }.program
            token <- Sessions.start(userId, global.config.sessionExpire).program
        } yield model.session.Session(userId, token, name)
        Program.run(prg, global.env)
    }

    def logout = Actions.authenticated(parse.empty) { (userId, timestamp, _) =>
        val prg = Sessions.remove(userId).program
        Program.run(prg, global.env)
    }
}
