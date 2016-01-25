package neo

import org.neo4j.graphdb.PropertyContainer

import scalaz._
import Scalaz._

case class Property[T](name: String, identifier: Option[String] = None) {
    def >>:(identifier: String): Property[T] = Property(name, Some(identifier))

    def =:=(value: Option[T])(implicit writer: NeoWriter[T]): PropertyValue = value match {
        case Some(v) => PropertyValue.NonEmpty(identifier, name, NeoValue.toNeo(v))
        case _ => PropertyValue.Empty
    }

    def =:=(value: T)(implicit writer: NeoWriter[T]) =
        PropertyValue.NonEmpty(identifier, name, NeoValue.toNeo(value))

    def from(container: PropertyContainer)(implicit reader: NeoReader[T]): ValidationNel[Throwable, T] =
        NeoValue.fromPropertyContainer(name, container)

    def from(row: Map[String, java.lang.Object])(implicit reader: NeoReader[T]): ValidationNel[Throwable, T] =
        Validation.fromTryCatchNonFatal[T] {
            val key = identifier match {
                case Some(id) => s"$id.$name"
                case _ => name
            }
            val value = NeoValue.fromRow(key, row)
            value.getOrElse(throw NeoException(s"Property $key not found"))
        }.toValidationNel
}

sealed trait PropertyValue

object PropertyValue {
    case object Empty extends PropertyValue
    case class NonEmpty(identifier: Option[String], name: String, value: java.lang.Object) extends PropertyValue
}
