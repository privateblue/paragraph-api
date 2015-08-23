package model

import neo.NeoValue
import neo.NeoValueWrites

import play.api.mvc._
import play.api.libs.json._

import scala.util.Right

case class UserId(val key: String) extends Id[String]

object UserId {
    implicit object UserIdWrites extends NeoValueWrites[UserId] {
        def write(v: UserId) = NeoValue(v.key)
    }

    implicit val userIdFormat = new Format[UserId] {
        def reads(json: JsValue) = JsSuccess(UserId(json.as[String]))
        def writes(id: UserId) = JsString(id.key)
    }
    implicit val pathBinder = new PathBindable[UserId] {
        override def bind(key: String, value: String): Either[String, UserId] = Right(UserId(value))
        override def unbind(key: String, userId: UserId): String = userId.key
    }
}
