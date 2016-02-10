package model.read

import model.base._

import play.api.libs.json._

case class User(
    userId: UserId,
    timestamp: Long,
    foreignId: String,
    name: String,
    avatar: Option[String]
)

object User {
    implicit val userWrites = Json.writes[User]
}
