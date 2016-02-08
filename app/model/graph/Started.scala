package model.graph

import model.base._

import play.api.libs.json._

case class Started(
    timestamp: Long,
    userId: Option[UserId],
    blockId: BlockId,
    body: BlockBody
)

object Started {
    implicit val startedFormat = Json.format[Started]
}
