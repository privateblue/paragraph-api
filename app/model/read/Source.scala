package model.read

import model.base._

import play.api.libs.json._

case class Source(
    timestamp: Long,
    blockId: BlockId,
    index: Long
)

object Source {
    implicit val sourceWrites = Json.writes[Source]
}
