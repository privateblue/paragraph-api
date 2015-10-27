package api.notification

import api._
import api.base._
import api.messaging.Messages

import model.notification._

import akka.stream.scaladsl.Source

import play.api.mvc._

class NotificationController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    import global.executionContext
    import global.system
    import global.materializer

    def get = Actions.authenticated(parse.empty) { (userId, timestamp, _) =>
        val prg = Notifications.get(userId).program
        Program.run(prg, global.env)
    }

    def dismiss = Actions.authenticated { (userId, timestamp, body) =>
        val notificationId = (body \ "notificationId").as[NotificationId]
        val prg = Notifications.dismiss(notificationId, userId).program
        Program.run(prg, global.env)
    }

    def subscribe = Actions.authenticatedSocket[Notification] { userId =>
        val prg = Messages.listen[Notification, Source[Notification, _]]("notification") { source =>
            source.collect {
                case n @ PathNotification(_, _, addresseeId, _, _) if addresseeId == userId => n
                case n @ UserNotification(_, _, addresseeId, _, _) if addresseeId == userId => n
            }
        }.program
        Program.run(prg, global.env)
    }
}
