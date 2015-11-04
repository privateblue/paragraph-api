package model.external

import model.base.BlockBody

sealed trait Paragraph

object Paragraph {
    case class Text(heading: Option[String], content: String, links: List[String]) extends Paragraph
    case class Image(heading: Option[String], url: String, links: List[String]) extends Paragraph

    def heading(paragraph: Paragraph) = paragraph match {
        case Paragraph.Text(heading, _, _) => heading
        case Paragraph.Image(heading, _, _) => heading
    }

    def blockBody(paragraph: Paragraph) = paragraph match {
        case Paragraph.Text(_, content, _) => BlockBody.Text(content)
        case Paragraph.Image(_, url, _) => BlockBody.Image(url)
    }
}
