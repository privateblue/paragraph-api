package model.graph

import model.base._

import play.api.libs.json._

case class Edited(
    blockId: BlockId,
    timestamp: Long,
    body: BlockBody
)

object Edited {
    implicit val editedFormat = Json.format[Edited]
}
