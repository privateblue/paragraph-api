package model.read

import model.base._

import play.api.libs.json._

case class Author(
    timestamp: Long,
    userId: UserId,
    userName: String
)

object Author {
    implicit val authorWrites = Json.writes[Author]
}
