package model

import play.api.libs.json._

case class Session(
    userId: UserId,
    token: String,
    name: String
)

object Session {
    implicit val sessionWrites = Json.writes[Session]
}
