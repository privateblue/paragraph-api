package model.paragraph

import model.base._

import play.api.libs.json._

case class Pinned(
    timestamp: Long,
    pageId: PageId,
    url: String,
    author: String,
    title: String,
    site: String
)

object Pinned {
    implicit val pinnedFormat = Json.format[Pinned]
}
