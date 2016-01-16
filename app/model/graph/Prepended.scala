package model.graph

import model.base._

import play.api.libs.json._

case class Prepended(
    blockId: BlockId,
    userId: UserId,
    timestamp: Long,
    target: BlockId,
    body: BlockBody
)

object Prepended {
    implicit val prependedFormat = Json.format[Prepended]
}
