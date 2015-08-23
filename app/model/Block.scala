package model

import play.api.libs.json._

case class Block(
    blockId: BlockId,
    title: Option[String],
    timestamp: Long,
    body: BlockBody,
    author: User,
    incoming: Seq[Connection],
    outgoing: Seq[Connection]
)

object Block {
    implicit val blockWrites = Json.writes[Block]
}
