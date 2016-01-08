package model.graph

import model.base._

import play.api.libs.json._

case class PageContinued(
    timestamp: Long,
    blockId: BlockId,
    target: BlockId,
    title: Option[String],
    blockBody: BlockBody
)

object PageContinued {
    implicit val pageContinuedFormat = Json.format[PageContinued]
}
