package model.base

import neo.NeoValue
import neo.NeoValueWrites
import neo.PropertyReader

import play.api.mvc._
import play.api.libs.json._

import scala.util.Right

case class BlockId(val key: String) extends Id[String] {
    override def toString = key
}

object BlockId {
    implicit object BlockIdWrites extends NeoValueWrites[BlockId] {
        def write(v: BlockId) = NeoValue(v.key)
    }

    implicit object BlockIdReader extends PropertyReader[BlockId] {
        def read(v: AnyRef) = BlockId(implicitly[PropertyReader[String]].read(v))
    }

    implicit val blockIdFormat = new Format[BlockId] {
        def reads(json: JsValue) = JsSuccess(BlockId(json.as[String]))
        def writes(id: BlockId) = JsString(id.key)
    }

    implicit val pathBinder = new PathBindable[BlockId] {
        override def bind(key: String, value: String): Either[String, BlockId] = Right(BlockId(value))
        override def unbind(key: String, blockId: BlockId): String = blockId.key
    }

    implicit val queryStringBinder = new QueryStringBindable[Seq[BlockId]] {
        override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Seq[BlockId]]] =
            params.get(key).map(ids => Right(ids.map(apply)))
        override def unbind(key: String, blockIds: Seq[BlockId]): String =
            blockIds.map(id => s"$key=$id").mkString("&")
    }

    implicit val blockIdOrdering = Ordering.by[BlockId, String](_.key)
}
