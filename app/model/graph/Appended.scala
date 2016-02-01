package model.graph

import model.base._

import play.api.libs.json._

case class Appended(
    blockId: BlockId,
    userId: Option[UserId],
    timestamp: Long,
    target: BlockId,
    body: BlockBody
)

object Appended {
    implicit val appendedFormat = Json.format[Appended]
}
