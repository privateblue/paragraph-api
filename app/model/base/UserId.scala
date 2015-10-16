package model.base

import neo.PropertyReader
import neo.PropertyWriter

import redis.ByteStringSerializer
import redis.ByteStringDeserializer

import play.api.mvc._
import play.api.libs.json._

import scala.util.Right

case class UserId(val key: String) extends Id[String] {
    override def toString = key
}

object UserId {
    implicit val userIdWriter = implicitly[PropertyWriter[String]].contramap[UserId](_.key)

    implicit val userIdReader = implicitly[PropertyReader[String]].map(UserId.apply)

    implicit val userIdFormat = new Format[UserId] {
        def reads(json: JsValue) = JsSuccess(UserId(json.as[String]))
        def writes(id: UserId) = JsString(id.key)
    }

    implicit val pathBinder = new PathBindable[UserId] {
        override def bind(key: String, value: String): Either[String, UserId] = Right(UserId(value))
        override def unbind(key: String, userId: UserId): String = userId.key
    }

    implicit val userIdSerializer = implicitly[ByteStringSerializer[String]].contramap[UserId](_.key)

    implicit val userIdDeserializer = implicitly[ByteStringDeserializer[String]].map(UserId.apply)
}
