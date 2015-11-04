package model.external

sealed trait Paragraph

object Paragraph {
    case class Text(heading: Option[String], content: String, links: List[String]) extends Paragraph
    case class Image(heading: Option[String], url: String, links: List[String]) extends Paragraph
}
