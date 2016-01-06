package model.paragraph

import model.base._

import play.api.libs.json._

case class RestDownloaded(
    timestamp: Long,
    blockId: BlockId,
    target: BlockId,
    title: Option[String],
    blockBody: BlockBody
)

object RestDownloaded {
    implicit val restDownloadedFormat = Json.format[RestDownloaded]
}
