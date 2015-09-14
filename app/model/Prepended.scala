package model

import play.api.libs.json._

case class Prepended(
    userId: UserId,
    timestamp: Long,
    target: BlockId,
    title: Option[String],
    body: BlockBody
)

object Prepended {
    implicit val prependedFormat = Json.format[Prepended]
}
