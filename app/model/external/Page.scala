package model.external

case class Page(
    author: String,
    title: String,
    site: String,
    paragraphs: List[Paragraph]
)
