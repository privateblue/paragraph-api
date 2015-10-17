package model.notification

import model.base.BlockId
import model.base.UserId

import redis.ByteStringSerializer
import redis.ByteStringDeserializer

import play.api.libs.json._

sealed trait Notification {
    def label = this match {
        case PathNotification(_, _, _, _, _) => Notification.Label.path
        case UserNotification(_, _, _, _, _) => Notification.Label.user
    }
}

case class PathNotification(
    notificationId: NotificationId,
    timestamp: Long,
    userId: UserId,
    text: String,
    path: List[BlockId]
) extends Notification

case class UserNotification(
    notificationId: NotificationId,
    timestamp: Long,
    userId: UserId,
    text: String,
    who: UserId
) extends Notification

object Notification {
    object Label {
        val path = "path"
        val user = "user"
    }

    implicit val pathNotificationFormat = Json.format[PathNotification]

    implicit val userNotificationFormat = Json.format[UserNotification]

    implicit val notificationFormat = new Format[Notification] {
        def reads(json: JsValue) = JsSuccess(
            (json \ "type").as[String] match {
                case Label.path => (json \ "notification").as[PathNotification]
                case Label.user => (json \ "notification").as[UserNotification]
            }
        )
        def writes(notification: Notification) = notification match {
            case n @ PathNotification(_, _, _, _, _) => Json.obj("type" -> Label.path, "notification" -> n)
            case n @ UserNotification(_, _, _, _, _) => Json.obj("type" -> Label.user, "notification" -> n)
        }
    }

    implicit val notificationSerializer = implicitly[ByteStringSerializer[String]].contramap[Notification](Json.toJson(_).toString)

    implicit val notificationDeserializer = implicitly[ByteStringDeserializer[String]].map(Json.parse(_).as[Notification])

    implicit val notificationOrdering = Ordering.by[Notification, (Long, NotificationId)] {
        case PathNotification(id, timestamp, _, _, _) => (timestamp, id)
        case UserNotification(id, timestamp, _, _, _) => (timestamp, id)
    }
}
