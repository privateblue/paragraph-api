package model.paragraph

import model.base._

import play.api.libs.json._

case class Continued(
    timestamp: Long,
    blockId: BlockId,
    target: BlockId,
    title: Option[String],
    blockBody: BlockBody
)

object Continued {
    implicit val continuedFormat = Json.format[Continued]
}
