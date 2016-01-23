package model.graph

import model.base._

import play.api.libs.json._

case class Included(
    timestamp: Long,
    url: String,
    author: Option[String],
    title: Option[String],
    site: Option[String]
)

object Included {
    implicit val includedFormat = Json.format[Included]
}
