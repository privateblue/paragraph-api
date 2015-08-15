package model

import neo.NeoValue
import neo.NeoValueWrites

case class BlockId(val key: String) extends Id[String]

object BlockId {
    implicit object BlockIdWrites extends NeoValueWrites[BlockId] {
        def write(v: BlockId) = NeoValue(v.key)
    }
}
