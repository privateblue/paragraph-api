package model.read

import model.base._

import play.api.libs.json._

case class Page(
    timestamp: Long,
    url: String,
    author: Option[String],
    title: Option[String],
    site: Option[String],
    published: Option[Long]
)

object Page {
    implicit val pageWrites = Json.writes[Page]
}
