package model.external

import model.base.BlockBody

sealed trait Paragraph

object Paragraph {
    case class Text(content: String, links: List[String]) extends Paragraph
    case class Heading(text: String) extends Paragraph
    case class Image(url: String) extends Paragraph

    def blockBody(paragraph: Paragraph) = paragraph match {
        case Paragraph.Text(content, links) => BlockBody.Text(content, links)
        case Paragraph.Heading(text) => BlockBody.Heading(text)
        case Paragraph.Image(url) => BlockBody.Image(url)
    }
}
