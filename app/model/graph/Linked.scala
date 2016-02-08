package model.graph

import model.base._

import play.api.libs.json._

case class Linked(
    timestamp: Long,
    userId: Option[UserId],
    from: BlockId,
    to: BlockId
)

object Linked {
    implicit val linkedFormat = Json.format[Linked]
}
