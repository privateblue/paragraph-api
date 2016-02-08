package model.graph

import model.base._

import play.api.libs.json._

case class Prepended(
    timestamp: Long,
    userId: Option[UserId],
    blockId: BlockId,
    target: BlockId,
    body: BlockBody
)

object Prepended {
    implicit val prependedFormat = Json.format[Prepended]
}
