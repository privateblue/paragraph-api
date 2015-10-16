package neo

import org.neo4j.graphdb.PropertyContainer

import scalaz._
import Scalaz._

case class Property[T](name: String, identifier: Option[String] = None) {
    def >>:(identifier: String): Property[T] = Property(name, Some(identifier))

    def =:=(value: Option[T])(implicit writer: PropertyWriter[T]): PropertyValue = value match {
        case Some(v) => PropertyValue.NonEmpty(identifier, name, NeoValue.toNeo(v))
        case _ => PropertyValue.Empty
    }

    def =:=(value: T)(implicit writer: PropertyWriter[T]) =
        PropertyValue.NonEmpty(identifier, name, NeoValue.toNeo(value))

    def from(container: PropertyContainer)(implicit reader: PropertyReader[T]): ValidationNel[Throwable, T] =
        NeoValue.fromPropertyContainer(name, container)

    def from(row: Map[String, AnyRef])(implicit reader: PropertyReader[T]): ValidationNel[Throwable, T] =
        Validation.fromTryCatchNonFatal[T] {
            identifier match {
                case Some(id) =>
                    val key = s"$id.$name"
                    val value = NeoValue.fromRow(key, row)
                    value.getOrElse(throw NeoException(s"Property $key not found"))
                case _ =>
                    throw NeoException(s"No identifier set for property $name")
            }
        }.toValidationNel
}

sealed trait PropertyValue

object PropertyValue {
    case object Empty extends PropertyValue
    case class NonEmpty(identifier: Option[String], name: String, value: AnyRef) extends PropertyValue
}
