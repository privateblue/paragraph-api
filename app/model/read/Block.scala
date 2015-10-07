package model.read

import model.base._

import play.api.libs.json._

case class Block(
    blockId: BlockId,
    title: Option[String],
    timestamp: Long,
    body: BlockBody,
    author: User,
    incoming: List[BlockConnection],
    outgoing: List[BlockConnection],
    views: List[Connection]
)

object Block {
    implicit val blockWrites = Json.writes[Block]
}
