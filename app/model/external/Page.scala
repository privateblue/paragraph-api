package model.external

case class Page(
    url: String,
    author: String,
    title: String,
    site: String,
    paragraphs: List[Paragraph]
)
