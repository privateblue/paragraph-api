package neo

case class Property(name: String) {
    def +[T: NeoValueWrites](value: Option[T]): PropertyValue = value match {
        case Some(v) => PropertyValue.NonEmpty(name, NeoValue(v))
        case _ => PropertyValue.Empty
    }
    def +[T: NeoValueWrites](value: T) = PropertyValue.NonEmpty(name, NeoValue(value))
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
