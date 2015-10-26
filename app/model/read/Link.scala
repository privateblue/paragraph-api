package model.read

import model.base._

import play.api.libs.json._

case class Link(
    timestamp: Long,
    blockId: BlockId,
    userId: Option[UserId]
)

object Link {
    implicit val LinkWrites = Json.writes[Link]
}
