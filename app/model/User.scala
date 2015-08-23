package model

import play.api.libs.json._

case class User(
    userId: UserId,
    timestamp: Long,
    foreignId: String,
    name: String
)

object User {
    implicit val userWrites = Json.writes[User]
}
