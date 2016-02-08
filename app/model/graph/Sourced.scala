package model.graph

import model.base._

import play.api.libs.json._

case class Sourced(
    timestamp: Long,
    url: String,
    blockId: BlockId,
    index: Long
)

object Sourced {
    implicit val sourcedFormat = Json.format[Sourced]
}
