package model

import neo.NeoValue
import neo.NeoValueWrites

import play.api.libs.json._

case class BlockId(val key: String) extends Id[String]

object BlockId {
    implicit object BlockIdWrites extends NeoValueWrites[BlockId] {
        def write(v: BlockId) = NeoValue(v.key)
    }

    implicit val blockIdFormat = new Format[BlockId] {
        def reads(json: JsValue) = JsSuccess(BlockId(json.as[String]))
        def writes(id: BlockId) = JsString(id.key)
    }
}
