package model.graph

import model.base._

import play.api.libs.json._

case class Pulled(
    timestamp: Long,
    pageId: PageId,
    url: String,
    author: String,
    title: String,
    site: String
)

object Pulled {
    implicit val pulledFormat = Json.format[Pulled]
}
