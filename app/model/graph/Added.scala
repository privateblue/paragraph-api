package model.graph

import model.base._

import play.api.libs.json._

case class Added(
    timestamp: Long,
    pageId: PageId,
    blockId: BlockId,
    title: Option[String],
    blockBody: BlockBody
)

object Added {
    implicit val addedFormat = Json.format[Added]
}
