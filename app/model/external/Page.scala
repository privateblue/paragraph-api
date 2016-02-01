package model.external

import scalaz._

case class Page(
    url: String,
    author: Option[String],
    title: Option[String],
    site: Option[String],
    paragraphs: NonEmptyList[Paragraph]
)
