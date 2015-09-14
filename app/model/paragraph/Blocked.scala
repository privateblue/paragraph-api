package model.paragraph

import model.base._

import play.api.libs.json._

case class Blocked(
    userId: UserId,
    timestamp: Long,
    target: UserId
)

object Blocked {
    implicit val blockedFormat = Json.format[Blocked]
}
