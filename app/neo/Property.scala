package neo

import org.neo4j.graphdb.PropertyContainer

import scalaz._
import Scalaz._

case class Property[T](name: String, identifier: Option[String] = None) {
    val key = identifier.map(id => s"$id.$name").getOrElse(name)

    def >>:(identifier: String): Property[T] = Property(name, Some(identifier))

    def =:=(value: T)(implicit writer: NeoWriter[T]) =
        PropertyValue.Single(name, writer.write(value))

    def =:=(value: Option[T])(implicit writer: NeoWriter[T]): PropertyValue =
        value.map(this =:= _).getOrElse(PropertyValue.Empty)

    def from(container: PropertyContainer)(implicit reader: NeoReader[T]): ValidationNel[Throwable, T] =
        Validation.fromTryCatchNonFatal[T] {
            val value = container.getProperty(name)
            reader.read(value)
        }.toValidationNel

    def from(row: Map[String, java.lang.Object])(implicit reader: NeoReader[T]): ValidationNel[Throwable, T] =
        Validation.fromTryCatchNonFatal[T] {
            val value = row.get(key).flatMap(Option(_)).map(reader.read(_))
            value.getOrElse(throw NeoException(s"Property $key not found"))
        }.toValidationNel
}

trait PropertyConverter[T] {
    def prop(value: T): PropertyValue
    def from(container: PropertyContainer): ValidationNel[Throwable, T]
    def from(row: Map[String, java.lang.Object]): ValidationNel[Throwable, T]
}

sealed trait PropertyValue

object PropertyValue {
    case object Empty extends PropertyValue
    case class Single(name: String, value: java.lang.Object) extends PropertyValue
    case class Multi(values: List[PropertyValue]) extends PropertyValue

    def apply[T](value: T)(implicit converter: PropertyConverter[T]) =
        converter.prop(value)
    def as[T](container: PropertyContainer)(implicit converter: PropertyConverter[T]): ValidationNel[Throwable, T] =
        converter.from(container)
    def as[T](row: Map[String, java.lang.Object])(implicit converter: PropertyConverter[T]): ValidationNel[Throwable, T] =
        converter.from(row)
}
