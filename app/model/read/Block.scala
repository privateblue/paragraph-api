package model.read

import model.base._

import play.api.libs.json._

case class Block(
    blockId: BlockId,
    timestamp: Long,
    modified: Long,
    body: BlockBody,
    author: Option[User],
    sources: List[Page]
)

object Block {
    implicit val blockWrites = Json.writes[Block]
}
