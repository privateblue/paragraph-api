package model.external

import model.base.BlockBody

sealed trait Paragraph

object Paragraph {
    case class Text(content: String, links: List[String]) extends Paragraph
    case class Title(text: String) extends Paragraph
    case class Image(url: String) extends Paragraph

    def blockBody(paragraph: Paragraph) = paragraph match {
        case Paragraph.Text(content, _) => BlockBody.Text(content)
        case Paragraph.Title(text) => BlockBody.Title(text)
        case Paragraph.Image(url) => BlockBody.Image(url)
    }
}
