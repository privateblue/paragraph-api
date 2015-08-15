package model

import neo.NeoValue
import neo.NeoValueWrites

import play.api.libs.json._

sealed trait BlockBody
case class Text(text: String) extends BlockBody
case class Image(uri: String) extends BlockBody

object BlockBody {
    implicit object BlockBodyWrites extends NeoValueWrites[BlockBody] {
        def write(body: BlockBody) = body match {
            case Text(text) => NeoValue(text)
            case Image(uri) => NeoValue(uri)
        }
    }

    implicit val textFormat = Json.format[Text]

    implicit val imageFormat = Json.format[Image]

    implicit val blockBodyFormat = new Format[BlockBody] {
        def reads(json: JsValue) = JsSuccess(
            (json \ "type").as[String] match {
                case "text" => (json \ "content").as[Text]
                case "image" => (json \ "content").as[Image]
            }
        )
        def writes(body: BlockBody) = body match {
            case b @ Text(_) => Json.obj("type" -> "text", "content" -> b)
            case b @ Image(_) => Json.obj("type" -> "image", "content" -> b)
        }
    }
}
