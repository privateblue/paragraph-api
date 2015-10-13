package model.paragraph

import model.base._

import play.api.libs.json._

case class Ignored(
    userId: UserId,
    timestamp: Long,
    target: UserId
)

object Ignored {
    implicit val ignoredFormat = Json.format[Ignored]
}
