package model.paragraph

import model.base._

import play.api.libs.json._

case class Viewed(
    userId: UserId,
    timestamp: Long,
    target: BlockId
)

object Viewed {
    implicit val viewedFormat = Json.format[Viewed]
}
