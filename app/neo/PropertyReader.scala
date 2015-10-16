package neo

trait PropertyReader[+T] { self =>
    def read(obj: AnyRef): T
    def map[U](fn: T => U): PropertyReader[U] =  new PropertyReader[U] {
        def read(v: AnyRef) = fn(self.read(v))
    }
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
