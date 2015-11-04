package model.paragraph

import model.base._

import play.api.libs.json._

case class PageRegistered(
    userId: PageId,
    timestamp: Long,
    url: String,
    author: String,
    title: String,
    site: String
)

object PageRegistered {
    implicit val pageRegisteredFormat = Json.format[PageRegistered]
}
