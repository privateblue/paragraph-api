package model.base

import neo.NeoReader
import neo.NeoWriter

import redis.ByteStringSerializer
import redis.ByteStringDeserializer

import play.api.mvc._
import play.api.libs.json._

trait Id[Key] {
    def key: Key
}

trait IdInstances[Key, T <: Id[Key]] {
    def apply(key: Key): T
    def fromString(value: String): Key

    private val parse = fromString _ andThen apply _

    implicit def idReader(implicit reader: NeoReader[Key]) = reader.map(apply)
    implicit def idWriter(implicit writer: NeoWriter[Key]) = writer.contramap[T](_.key)

    implicit def idFormat(implicit format: Format[Key]) = new Format[T] {
        def reads(json: JsValue) = JsSuccess(apply(json.as[Key]))
        def writes(id: T) = JsString(id.key.toString)
    }

    implicit val pathBinder = new PathBindable[T] {
        override def bind(key: String, value: String): Either[String, T] = Right(parse(value))
        override def unbind(key: String, id: T): String = id.key.toString
    }

    implicit val queryStringBinder = new QueryStringBindable[Seq[T]] {
        override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Seq[T]]] =
            params.get(key).map(ids => Right(ids.map(parse)))
        override def unbind(key: String, ids: Seq[T]): String =
            ids.map(id => s"$key=$id").mkString("&")
    }

    implicit def idDeserializer(implicit reader: ByteStringDeserializer[Key]) = reader.map(apply)
    implicit def idSerializer(implicit writer: ByteStringSerializer[Key]) = writer.contramap[T](_.key)

    implicit def idOrdering(implicit ord: Ordering[Key]) = Ordering.by[T, Key](_.key)
}
