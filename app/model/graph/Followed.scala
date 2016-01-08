package model.graph

import model.base._

import play.api.libs.json._

case class Followed(
    userId: UserId,
    timestamp: Long,
    target: UserId
)

object Followed {
    implicit val followedFormat = Json.format[Followed]
}
