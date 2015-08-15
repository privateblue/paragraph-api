package model

import neo.NeoValue
import neo.NeoValueWrites

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
}
