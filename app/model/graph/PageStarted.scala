package model.graph

import model.base._

import play.api.libs.json._

case class PageStarted(
    blockId: BlockId,
    pageId: PageId,
    timestamp: Long,
    title: Option[String],
    body: BlockBody
)

object PageStarted {
    implicit val pageStartedFormat = Json.format[PageStarted]
}
