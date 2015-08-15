package model

import neo.NeoValue
import neo.NeoValueWrites

import play.api.libs.json._

case class UserId(val key: String) extends Id[String]

object UserId {
    implicit object UserIdWrites extends NeoValueWrites[UserId] {
        def write(v: UserId) = NeoValue(v.key)
    }

    implicit val userIdFormat = new Format[UserId] {
        def reads(json: JsValue) = JsSuccess(UserId(json.as[String]))
        def writes(id: UserId) = JsString(id.key)
    }
}
