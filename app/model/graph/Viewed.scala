package model.graph

import model.base._

import play.api.libs.json._

case class Viewed(
    userId: UserId,
    userName: String,
    timestamp: Long,
    target: BlockId
)

object Viewed {
    implicit val viewedFormat = Json.format[Viewed]
}
