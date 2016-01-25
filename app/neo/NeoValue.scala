package neo

import org.neo4j.graphdb.PropertyContainer

import scalaz._

object NeoValue {
    def toNeo[T](value: T)(implicit writer: NeoWriter[T]): java.lang.Object = writer.write(value)

    def fromNeo[T](value: java.lang.Object)(implicit reader: NeoReader[T]): T = reader.read(value)

    def fromPropertyContainer[T: NeoReader](name: String, container: PropertyContainer): ValidationNel[Throwable, T] =
        Validation.fromTryCatchNonFatal[T] {
            val value = container.getProperty(name)
            fromNeo(value)
        }.toValidationNel

    def fromRow[T: NeoReader](name: String, row: Map[String, java.lang.Object]) =
        row.get(name).flatMap(Option(_)).map(fromNeo(_))
}
