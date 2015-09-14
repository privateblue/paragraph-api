package model

import play.api.libs.json._

case class Unblocked(
    userId: UserId,
    timestamp: Long,
    target: UserId
)

object Unblocked {
    implicit val unblockedFormat = Json.format[Unblocked]
}
