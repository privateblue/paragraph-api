package model

import play.api.libs.json._

case class BlockConnection(
    connection: Connection,
    blockId: BlockId
)

object BlockConnection {
    implicit val blockConnectionWrites = Json.writes[BlockConnection]
}
