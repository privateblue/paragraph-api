package model

import neo.NeoValue
import neo.NeoValueWrites
import neo.PropertyReader

import play.api.libs.json._

sealed trait BlockBody {
    val label: String = this match {
        case Text(_) => BlockBody.Label.text
        case Image(_) => BlockBody.Label.image
    }
}

case class Text(text: String) extends BlockBody

case class Image(uri: String) extends BlockBody

object BlockBody {
    object Label {
        val text = "text"
        val image = "image"
    }

    implicit object BlockBodyWrites extends NeoValueWrites[BlockBody] {
        def write(body: BlockBody) = body match {
            case Text(text) => NeoValue(text)
            case Image(uri) => NeoValue(uri)
        }
    }

    implicit object TextReader extends PropertyReader[Text] {
        def read(v: AnyRef) = Text(implicitly[PropertyReader[String]].read(v))
    }

    implicit object ImageReader extends PropertyReader[Image] {
        def read(v: AnyRef) = Image(implicitly[PropertyReader[String]].read(v))
    }

    implicit val textFormat = Json.format[Text]

    implicit val imageFormat = Json.format[Image]

    implicit val blockBodyFormat = new Format[BlockBody] {
        def reads(json: JsValue) = JsSuccess(
            (json \ "type").as[String] match {
                case Label.text => (json \ "content").as[Text]
                case Label.image => (json \ "content").as[Image]
            }
        )
        def writes(body: BlockBody) = body match {
            case b @ Text(_) => Json.obj("type" -> Label.text, "content" -> b)
            case b @ Image(_) => Json.obj("type" -> Label.image, "content" -> b)
        }
    }
}
