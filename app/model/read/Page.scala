package model.read

import model.base._

import play.api.libs.json._

case class Page(
    pageId: PageId,
    timestamp: Long,
    url: String,
    author: String,
    title: String,
    site: String
)

object Page {
    implicit val pageWrites = Json.writes[Page]
}
