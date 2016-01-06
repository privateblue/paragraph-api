package model.paragraph

import model.base._

import play.api.libs.json._

case class FirstDownloaded(
    timestamp: Long,
    pageId: PageId,
    blockId: BlockId,
    title: Option[String],
    blockBody: BlockBody
)

object FirstDownloaded {
    implicit val firstDownloadedFormat = Json.format[FirstDownloaded]
}
