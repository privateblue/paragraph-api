package model.external

case class Page(
    url: String,
    author: Option[String],
    title: Option[String],
    site: Option[String],
    paragraphs: List[Paragraph]
)
