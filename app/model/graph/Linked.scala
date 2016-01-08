package model.graph

import model.base._

import play.api.libs.json._

case class Linked(
    userId: Option[UserId],
    timestamp: Long,
    from: BlockId,
    to: BlockId
)

object Linked {
    implicit val linkedFormat = Json.format[Linked]
}
