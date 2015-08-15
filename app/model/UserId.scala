package model

import neo.NeoValue
import neo.NeoValueWrites

case class UserId(val key: String) extends Id[String]

object UserId {
    implicit object UserIdWrites extends NeoValueWrites[UserId] {
        def write(v: UserId) = NeoValue(v.key)
    }
}
