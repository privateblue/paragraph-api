package model.external

sealed trait Paragraph
case class Text(title: Option[String], content: String, links: List[String]) extends Paragraph
case class Image(title: Option[String], url: String, links: List[String]) extends Paragraph
