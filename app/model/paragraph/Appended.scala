package model.paragraph

import model.base._

import play.api.libs.json._

case class Appended(
    blockId: BlockId,
    userId: UserId,
    timestamp: Long,
    target: BlockId,
    title: Option[String],
    body: BlockBody
)

object Appended {
    implicit val appendedFormat = Json.format[Appended]
}
