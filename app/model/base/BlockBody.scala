package model.base

import play.api.libs.json._

sealed trait BlockBody

object BlockBody {
    case class Text(text: String) extends BlockBody
    case class Title(text: String) extends BlockBody
    case class Image(uri: String) extends BlockBody

    object Label {
        val text = "text"
        val title = "title"
        val image = "image"
    }

    implicit val textFormat = Json.format[Text]

    implicit val titleFormat = Json.format[Title]

    implicit val imageFormat = Json.format[Image]

    implicit val blockBodyFormat = new Format[BlockBody] {
        def reads(json: JsValue) = JsSuccess(
            (json \ "label").as[String] match {
                case Label.text => (json \ "content").as[Text]
                case Label.title => (json \ "content").as[Title]
                case Label.image => (json \ "content").as[Image]
            }
        )
        def writes(body: BlockBody) = body match {
            case b @ Text(_) => Json.obj("label" -> Label.text, "content" -> b)
            case b @ Title(_) => Json.obj("label" -> Label.title, "content" -> b)
            case b @ Image(_) => Json.obj("label" -> Label.image, "content" -> b)
        }
    }
}
