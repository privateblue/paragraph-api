package neo

trait PropertyWriter[-T] { self =>
    def write(v: T): AnyRef
    def contramap[U](fn: U => T): PropertyWriter[U] =  new PropertyWriter[U] {
        def write(v: U) = self.write(fn(v))
    }
}

object PropertyWriter {
    implicit object BooleanWriter extends PropertyWriter[Boolean] {
        def write(v: Boolean) = v.asInstanceOf[AnyRef]
    }
    implicit object ByteWriter extends PropertyWriter[Byte] {
        def write(v: Byte) = v.asInstanceOf[AnyRef]
    }
    implicit object ShortWriter extends PropertyWriter[Short] {
        def write(v: Short) = v.asInstanceOf[AnyRef]
    }
    implicit object IntWriter extends PropertyWriter[Int] {
        def write(v: Int) = v.asInstanceOf[AnyRef]
    }
    implicit object LongWriter extends PropertyWriter[Long] {
        def write(v: Long) = v.asInstanceOf[AnyRef]
    }
    implicit object FloatWriter extends PropertyWriter[Float] {
        def write(v: Float) = v.asInstanceOf[AnyRef]
    }
    implicit object DoubleWriter extends PropertyWriter[Double] {
        def write(v: Double) = v.asInstanceOf[AnyRef]
    }
    implicit object CharWriter extends PropertyWriter[Char] {
        def write(v: Char) = v.asInstanceOf[AnyRef]
    }
    implicit object StringWriter extends PropertyWriter[String] {
        def write(v: String) = v.asInstanceOf[AnyRef]
    }
    implicit def traversableWriter[T: PropertyWriter] = new PropertyWriter[Traversable[T]] {
        def write(v: Traversable[T]) = v.map(implicitly[PropertyWriter[T]].write(_)).toArray
    }
}
