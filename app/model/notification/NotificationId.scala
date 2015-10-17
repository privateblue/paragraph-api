package model.notification

import model.base.Id

import redis.ByteStringSerializer
import redis.ByteStringDeserializer

import play.api.mvc._
import play.api.libs.json._

import scala.util.Right

case class NotificationId(val key: String) extends Id[String] {
    override def toString = key
}

object NotificationId {
    implicit val notificationIdFormat = new Format[NotificationId] {
        def reads(json: JsValue) = JsSuccess(NotificationId(json.as[String]))
        def writes(id: NotificationId) = JsString(id.key)
    }

    implicit val pathBinder = new PathBindable[NotificationId] {
        override def bind(key: String, value: String): Either[String, NotificationId] = Right(NotificationId(value))
        override def unbind(key: String, notificationId: NotificationId): String = notificationId.key
    }

    implicit val queryStringBinder = new QueryStringBindable[Seq[NotificationId]] {
        override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Seq[NotificationId]]] =
            params.get(key).map(ids => Right(ids.map(apply)))
        override def unbind(key: String, notificationIds: Seq[NotificationId]): String =
            notificationIds.map(id => s"$key=$id").mkString("&")
    }

    implicit val notificationIdSerializer = implicitly[ByteStringSerializer[String]].contramap[NotificationId](_.key)

    implicit val notificationIdDeserializer = implicitly[ByteStringDeserializer[String]].map(NotificationId.apply)

    implicit val notificationIdOrdering = Ordering.by[NotificationId, String](_.key)
}
