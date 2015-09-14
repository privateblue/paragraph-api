package model

import play.api.libs.json._

case class Linked(
    userId: UserId,
    timestamp: Long,
    form: BlockId,
    to: BlockId
)

object Linked {
    implicit val linkedFormat = Json.format[Linked]
}
