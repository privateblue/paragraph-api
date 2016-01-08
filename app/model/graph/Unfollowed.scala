package model.graph

import model.base._

import play.api.libs.json._

case class Unfollowed(
    userId: UserId,
    timestamp: Long,
    target: UserId
)

object Unfollowed {
    implicit val unfollowedFormat = Json.format[Unfollowed]
}
