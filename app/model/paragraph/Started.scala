package model.paragraph

import model.base._

import play.api.libs.json._

case class Started(
    blockId: BlockId,
    userId: UserId,
    timestamp: Long,
    title: Option[String],
    body: BlockBody
)

object Started {
    implicit val startedFormat = Json.format[Started]
}
