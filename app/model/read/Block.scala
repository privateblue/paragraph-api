package model.read

import model.base._

import play.api.libs.json._

case class Block(
    blockId: BlockId,
    title: Option[String],
    timestamp: Long,
    body: BlockBody,
    author: User,
    incoming: Seq[BlockConnection],
    outgoing: Seq[BlockConnection],
    seen: Seq[Connection]
)

object Block {
    implicit val blockWrites = Json.writes[Block]
}
