package model.graph

import model.base._

import play.api.libs.json._

case class Downloaded(
    timestamp: Long,
    pageId: PageId,
    url: String,
    author: Option[String],
    title: Option[String],
    site: Option[String]
)

object Downloaded {
    implicit val downloadedFormat = Json.format[Downloaded]
}
