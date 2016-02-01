package model.graph

import model.base._

import play.api.libs.json._

case class Started(
    blockId: BlockId,
    userId: Option[UserId],
    timestamp: Long,
    body: BlockBody
)

object Started {
    implicit val startedFormat = Json.format[Started]
}
