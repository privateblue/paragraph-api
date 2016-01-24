package neo

trait PropertyReader[+T] { self =>
    def read(obj: java.lang.Object): T
    def map[U](fn: T => U): PropertyReader[U] =  new PropertyReader[U] {
        def read(v: java.lang.Object) = fn(self.read(v))
    }
}

object PropertyReader {
    implicit object BooleanReader extends PropertyReader[Boolean] {
        def read(v: java.lang.Object) = v.asInstanceOf[Boolean]
    }
    implicit object ByteReader extends PropertyReader[Byte] {
        def read(v: java.lang.Object) = v.asInstanceOf[Byte]
    }
    implicit object ShortReader extends PropertyReader[Short] {
        def read(v: java.lang.Object) = v.asInstanceOf[Short]
    }
    implicit object IntReader extends PropertyReader[Int] {
        def read(v: java.lang.Object) = v.asInstanceOf[Int]
    }
    implicit object LongReader extends PropertyReader[Long] {
        def read(v: java.lang.Object) = v.asInstanceOf[Long]
    }
    implicit object FloatReader extends PropertyReader[Float] {
        def read(v: java.lang.Object) = v.asInstanceOf[Float]
    }
    implicit object DoubleReader extends PropertyReader[Double] {
        def read(v: java.lang.Object) = v.asInstanceOf[Double]
    }
    implicit object CharReader extends PropertyReader[Char] {
        def read(v: java.lang.Object) = v.asInstanceOf[Char]
    }
    implicit object StringReader extends PropertyReader[String] {
        def read(v: java.lang.Object) = v.asInstanceOf[String]
    }
    implicit def traversableReader[T: PropertyReader] = new PropertyReader[Traversable[T]] {
        def read(v: java.lang.Object) = v.asInstanceOf[Array[java.lang.Object]].map(elem => implicitly[PropertyReader[T]].read(elem)).toTraversable
    }
}
