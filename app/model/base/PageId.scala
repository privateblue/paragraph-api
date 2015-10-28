package model.base

import neo.PropertyReader
import neo.PropertyWriter

import redis.ByteStringSerializer
import redis.ByteStringDeserializer

import play.api.mvc._
import play.api.libs.json._

import scala.util.Right

case class PageId(val key: String) extends Id[String] {
    override def toString = key
}

object PageId extends IdInstances[String, PageId] {
    def fromString(value: String) = value
}
