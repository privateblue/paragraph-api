package model.graph

import model.base._

import play.api.libs.json._

case class Attached(
    timestamp: Long,
    userId: UserId,
    pageId: PageId,
    blockId: BlockId,
    blockBody: BlockBody
)

object Attached {
    implicit val attachedFormat = Json.format[Attached]
}
