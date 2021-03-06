package model.base

import neo.NeoReader
import neo.NeoWriter

import play.api.mvc._
import play.api.libs.json._

import scala.util.Right

case class BlockId(val key: String) extends Id[String] {
    override def toString = key
}

object BlockId extends IdInstances[String, BlockId] {
    def fromString(value: String) = value
}
