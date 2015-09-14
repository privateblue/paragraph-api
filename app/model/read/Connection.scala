package model.read

import model.base._

import neo.Arrow

import play.api.libs.json._

case class Connection(
    userId: UserId,
    timestamp: Long,
    arrow: Arrow
)

object Connection {
    implicit val connectionWrites = Json.writes[Connection]
}
