package model.graph

import model.base._

import play.api.libs.json._

case class Appended(
    timestamp: Long,
    userId: Option[UserId],
    blockId: BlockId,
    target: BlockId,
    body: BlockBody
)

object Appended {
    implicit val appendedFormat = Json.format[Appended]
}
