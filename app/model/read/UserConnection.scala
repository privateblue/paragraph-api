package model.read

import model.base._

import play.api.libs.json._

case class UserConnection(
    connection: Connection,
    userId: UserId,
    userName: String
)

object UserConnection {
    implicit val userConnectionWrites = Json.writes[UserConnection]
}
