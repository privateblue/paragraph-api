package model.base

import play.api.libs.json._

sealed trait BlockBody

object BlockBody {
    case class Text(text: String, externalLinks: List[String]) extends BlockBody
    case class Heading(text: String) extends BlockBody
    case class Image(uri: String) extends BlockBody

    object Label {
        val text = "text"
        val heading = "heading"
        val image = "image"
    }

    implicit val textFormat = new Format[Text] {
        def reads(json: JsValue) = JsSuccess(Text((json \ "text").as[String], List()))
        def writes(text: Text) = Json.obj("text" -> text.text, "externalLinks" -> text.externalLinks)
    }

    implicit val headingFormat = Json.format[Heading]

    implicit val imageFormat = Json.format[Image]

    implicit val blockBodyFormat = new Format[BlockBody] {
        def reads(json: JsValue) = JsSuccess(
            (json \ "label").as[String] match {
                case Label.text => (json \ "content").as[Text]
                case Label.heading => (json \ "content").as[Heading]
                case Label.image => (json \ "content").as[Image]
            }
        )
        def writes(body: BlockBody) = body match {
            case b @ Text(_, _) => Json.obj("label" -> Label.text, "content" -> b)
            case b @ Heading(_) => Json.obj("label" -> Label.heading, "content" -> b)
            case b @ Image(_) => Json.obj("label" -> Label.image, "content" -> b)
        }
    }
}
