package model.graph

import model.base._

import play.api.libs.json._

case class Registered(
    userId: UserId,
    timestamp: Long,
    foreignId: String,
    name: String,
    password: String
)

object Registered {
    implicit val registeredFormat = Json.format[Registered]
}
