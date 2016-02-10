package model.graph

import model.base._

import play.api.libs.json._

case class Registered(
    timestamp: Long,
    userId: UserId,
    name: String,
    hash: String,
    foreignId: String,
    avatar: Option[String]
)

object Registered {
    implicit val registeredFormat = Json.format[Registered]
}
