package neo

sealed trait NeoValue {
    def underlying: AnyRef = this match {
        case BooleanValue(value) => value.asInstanceOf[AnyRef]
        case ByteValue(value) => value.asInstanceOf[AnyRef]
        case ShortValue(value) => value.asInstanceOf[AnyRef]
        case IntValue(value) => value.asInstanceOf[AnyRef]
        case LongValue(value) => value.asInstanceOf[AnyRef]
        case FloatValue(value) => value.asInstanceOf[AnyRef]
        case DoubleValue(value) => value.asInstanceOf[AnyRef]
        case CharValue(value) => value.asInstanceOf[AnyRef]
        case StringValue(value) => value.asInstanceOf[AnyRef]
        case ArrayValue(value) => value.map(_.underlying).toArray
    }

    override def toString = underlying.toString
}
case class BooleanValue(value: Boolean) extends NeoValue
case class ByteValue(value: Byte) extends NeoValue
case class ShortValue(value: Short) extends NeoValue
case class IntValue(value: Int) extends NeoValue
case class LongValue(value: Long) extends NeoValue
case class FloatValue(value: Float) extends NeoValue
case class DoubleValue(value: Double) extends NeoValue
case class CharValue(value: Char) extends NeoValue
case class StringValue(value: String) extends NeoValue
case class ArrayValue(value: Seq[NeoValue]) extends NeoValue

object NeoValue {
    def apply[T: NeoValueWrites](v: T) = implicitly[NeoValueWrites[T]].write(v)
    def unapply(nv: NeoValue): Option[AnyRef] = Some(nv.underlying)
}

trait NeoValueWrites[-T] {
    def write(v: T): NeoValue
}

object NeoValueWrites {
    implicit object BooleanWrites extends NeoValueWrites[Boolean] {
        def write(v: Boolean) = BooleanValue(v)
    }
    implicit object ByteWrites extends NeoValueWrites[Byte] {
        def write(v: Byte) = ByteValue(v)
    }
    implicit object ShortWrites extends NeoValueWrites[Short] {
        def write(v: Short) = ShortValue(v)
    }
    implicit object IntWrites extends NeoValueWrites[Int] {
        def write(v: Int) = IntValue(v)
    }
    implicit object LongWrites extends NeoValueWrites[Long] {
        def write(v: Long) = LongValue(v)
    }
    implicit object FloatWrites extends NeoValueWrites[Float] {
        def write(v: Float) = FloatValue(v)
    }
    implicit object DoubleWrites extends NeoValueWrites[Double] {
        def write(v: Double) = DoubleValue(v)
    }
    implicit object CharWrites extends NeoValueWrites[Char] {
        def write(v: Char) = CharValue(v)
    }
    implicit object StringWrites extends NeoValueWrites[String] {
        def write(v: String) = StringValue(v)
    }
    implicit def traversableWrites[T: NeoValueWrites] = new NeoValueWrites[Traversable[T]] {
        def write(v: Traversable[T]) = ArrayValue(v.map(NeoValue(_)).toSeq)
    }
}
