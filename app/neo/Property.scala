package neo

import org.neo4j.graphdb.PropertyContainer

import scalaz._

case class Property[T](name: String) {

    def =:=(value: Option[T])(implicit nvw: NeoValueWrites[T]): PropertyValue = value match {
        case Some(v) => PropertyValue.NonEmpty(name, nvw.write(v))
        case _ => PropertyValue.Empty
    }

    def =:=(value: T)(implicit nvw: NeoValueWrites[T]) = PropertyValue.NonEmpty(name, nvw.write(value))

    def from(container: PropertyContainer)(implicit reader: PropertyReader[T]): ValidationNel[Throwable, T] =
        Validation.fromTryCatchNonFatal[T] {
            val v = container.getProperty(name)
            reader.read(v)
        }.toValidationNel
}

sealed trait PropertyValue

object PropertyValue {
    case object Empty extends PropertyValue {
        def of(alias: String) = Empty
    }

    case class NonEmpty(name: String, value: NeoValue) extends PropertyValue {
        def of(alias: String) = Aliased(alias, name, value)
    }

    case class Aliased(alias: String, name: String, value: NeoValue) extends PropertyValue
}

trait PropertyReader[+T] {
    def read(obj: AnyRef): T
}

object PropertyReader {
    implicit object BooleanReader extends PropertyReader[Boolean] {
        def read(v: AnyRef) = v.asInstanceOf[Boolean]
    }
    implicit object ByteReader extends PropertyReader[Byte] {
        def read(v: AnyRef) = v.asInstanceOf[Byte]
    }
    implicit object ShortReader extends PropertyReader[Short] {
        def read(v: AnyRef) = v.asInstanceOf[Short]
    }
    implicit object IntReader extends PropertyReader[Int] {
        def read(v: AnyRef) = v.asInstanceOf[Int]
    }
    implicit object LongReader extends PropertyReader[Long] {
        def read(v: AnyRef) = v.asInstanceOf[Long]
    }
    implicit object FloatReader extends PropertyReader[Float] {
        def read(v: AnyRef) = v.asInstanceOf[Float]
    }
    implicit object DoubleReader extends PropertyReader[Double] {
        def read(v: AnyRef) = v.asInstanceOf[Double]
    }
    implicit object CharReader extends PropertyReader[Char] {
        def read(v: AnyRef) = v.asInstanceOf[Char]
    }
    implicit object StringReader extends PropertyReader[String] {
        def read(v: AnyRef) = v.asInstanceOf[String]
    }
    implicit def traversableReader[T: PropertyReader] = new PropertyReader[Traversable[T]] {
        def read(v: AnyRef) = v.asInstanceOf[Array[AnyRef]].map(elem => implicitly[PropertyReader[T]].read(elem)).toTraversable
    }
}
