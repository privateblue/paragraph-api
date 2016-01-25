package model.base

import neo.NeoReader
import neo.NeoWriter

import play.api.libs.json._

sealed trait BlockBody {
    val label: String = this match {
        case BlockBody.Text(_) => BlockBody.Label.text
        case BlockBody.Title(_) => BlockBody.Label.title
        case BlockBody.Image(_) => BlockBody.Label.image
    }
}

object BlockBody {
    case class Text(text: String) extends BlockBody
    case class Title(text: String) extends BlockBody
    case class Image(uri: String) extends BlockBody

    object Label {
        val text = "text"
        val title = "title"
        val image = "image"
    }

    implicit object BlockBodyWriter extends NeoWriter[BlockBody] {
        def write(body: BlockBody) = body match {
            case Text(text) => implicitly[NeoWriter[String]].write(text)
            case Title(text) => implicitly[NeoWriter[String]].write(text)
            case Image(uri) => implicitly[NeoWriter[String]].write(uri)
        }
    }

    implicit val textReader = implicitly[NeoReader[String]].map(Text.apply)

    implicit val titleReader = implicitly[NeoReader[String]].map(Title.apply)

    implicit val imageReader = implicitly[NeoReader[String]].map(Image.apply)

    implicit val textFormat = Json.format[Text]

    implicit val titleFormat = Json.format[Title]

    implicit val imageFormat = Json.format[Image]

    implicit val blockBodyFormat = new Format[BlockBody] {
        def reads(json: JsValue) = JsSuccess(
            (json \ "type").as[String] match {
                case Label.text => (json \ "content").as[Text]
                case Label.title => (json \ "content").as[Title]
                case Label.image => (json \ "content").as[Image]
            }
        )
        def writes(body: BlockBody) = body match {
            case b @ Text(_) => Json.obj("type" -> Label.text, "content" -> b)
            case b @ Title(_) => Json.obj("type" -> Label.title, "content" -> b)
            case b @ Image(_) => Json.obj("type" -> Label.image, "content" -> b)
        }
    }
}
