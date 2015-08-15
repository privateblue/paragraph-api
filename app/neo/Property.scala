package neo

sealed trait Property[T]
case object EmptyProperty extends Property[Nothing]
abstract class NonEmptyProperty[T: NeoValueWrites] extends Property[T] {
    val key: String
    val value: T
    val neoValue = implicitly[NeoValueWrites[T]].write(value)
    val toPropString = s"${key}:{${key}}"
    val toParam = key -> neoValue.underlying
}
case class NonUniqueProperty[T: NeoValueWrites](key: String, value: T) extends NonEmptyProperty[T] {
    def unique = UniqueProperty(key, value)
}
case class UniqueProperty[T: NeoValueWrites](key: String, value: T) extends NonEmptyProperty[T]

object Property {
    implicit class Collection(properties: Seq[Property[_]]) {
        val uniques: Seq[UniqueProperty[_]] = properties.collect {
            case p @ UniqueProperty(_, _) => p
        }

        val nonUniques: Seq[NonUniqueProperty[_]] = properties.collect {
            case p @ NonUniqueProperty(_, _) => p
        }

        val nonEmpties: Seq[NonEmptyProperty[_]] = properties.collect {
            case p @ UniqueProperty(_, _) => p
            case p @ NonUniqueProperty(_, _) => p
        }

        def toPropString = nonEmpties.map(_.toPropString).mkString("{", ", ", "}")

        def toParams = nonEmpties.map(_.toParam).toMap
    }
}
