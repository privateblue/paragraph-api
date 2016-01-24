package neo

import org.neo4j.graphdb.PropertyContainer

import scalaz._

object NeoValue {
    def toNeo[T](value: T)(implicit writer: PropertyWriter[T]): java.lang.Object = writer.write(value)

    def fromNeo[T](value: java.lang.Object)(implicit reader: PropertyReader[T]): T = reader.read(value)

    def fromPropertyContainer[T: PropertyReader](name: String, container: PropertyContainer): ValidationNel[Throwable, T] =
        Validation.fromTryCatchNonFatal[T] {
            val value = container.getProperty(name)
            fromNeo(value)
        }.toValidationNel

    def fromRow[T: PropertyReader](name: String, row: Map[String, AnyRef]) =
        row.get(name).flatMap(Option(_)).map(fromNeo(_))
}
