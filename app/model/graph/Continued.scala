package model.graph

import model.base._

import play.api.libs.json._

case class Continued(
    timestamp: Long,
    userId: UserId,
    pageId: PageId,
    blockId: BlockId,
    target: BlockId,
    blockBody: BlockBody
)

object Continued {
    implicit val continuedFormat = Json.format[Continued]
}
