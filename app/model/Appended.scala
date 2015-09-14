package model

import play.api.libs.json._

case class Appended(
    userId: UserId,
    timestamp: Long,
    target: BlockId,
    title: Option[String],
    body: BlockBody
)

object Appended {
    implicit val appendedFormat = Json.format[Appended]
}
