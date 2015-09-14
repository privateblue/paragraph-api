package model

import play.api.libs.json._

case class Unfollowed(
    userId: UserId,
    timestamp: Long,
    target: UserId
)

object Unfollowed {
    implicit val unfollowedFormat = Json.format[Unfollowed]
}
