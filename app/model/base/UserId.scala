package model.base

import neo.PropertyReader
import neo.PropertyWriter

import redis.ByteStringSerializer
import redis.ByteStringDeserializer

import play.api.mvc._
import play.api.libs.json._

import scala.util.Right

case class UserId(val key: String) extends Id[String] {
    override def toString = key
}

object UserId extends IdInstances[String, UserId] {
    def fromString(value: String) = value
}
