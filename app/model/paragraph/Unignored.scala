package model.paragraph

import model.base._

import play.api.libs.json._

case class Unignored(
    userId: UserId,
    timestamp: Long,
    target: UserId
)

object Unignored {
    implicit val unignoredFormat = Json.format[Unignored]
}
