package model.external

import model.base.BlockBody

sealed trait Paragraph

object Paragraph {
    case class Text(content: String, links: List[String]) extends Paragraph
    case class Title(text: String, links: List[String]) extends Paragraph
    case class Image(url: String, links: List[String]) extends Paragraph

    def blockBody(paragraph: Paragraph) = paragraph match {
        case Paragraph.Text(content, _) => BlockBody.Text(content)
        case Paragraph.Title(text, _) => BlockBody.Title(text)
        case Paragraph.Image(url, _) => BlockBody.Image(url)
    }
}
