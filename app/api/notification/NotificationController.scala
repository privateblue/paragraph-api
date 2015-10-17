package api.notification

import api._
import api.base._

import model.notification._

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

class NotificationController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    def get = Actions.authenticated(parse.empty) { (userId, timestamp, _) =>
        val prg = Notifications.get(userId).program
        Program.run(prg, global.env)
    }

    def dismiss = Actions.authenticated { (userId, timestamp, body) =>
        val notificationId = (body \ "notificationId").as[NotificationId]
        val prg = Notifications.dismiss(notificationId, userId).program
        Program.run(prg, global.env)
    }
}
