package neo

trait NeoReader[+T] { self =>
    def read(obj: java.lang.Object): T
    def map[U](fn: T => U): NeoReader[U] =  new NeoReader[U] {
        def read(v: java.lang.Object) = fn(self.read(v))
    }
}

object NeoReader {
    implicit object BooleanReader extends NeoReader[Boolean] {
        def read(v: java.lang.Object) = v.asInstanceOf[Boolean]
    }
    implicit object ByteReader extends NeoReader[Byte] {
        def read(v: java.lang.Object) = v.asInstanceOf[Byte]
    }
    implicit object ShortReader extends NeoReader[Short] {
        def read(v: java.lang.Object) = v.asInstanceOf[Short]
    }
    implicit object IntReader extends NeoReader[Int] {
        def read(v: java.lang.Object) = v.asInstanceOf[Int]
    }
    implicit object LongReader extends NeoReader[Long] {
        def read(v: java.lang.Object) = v.asInstanceOf[Long]
    }
    implicit object FloatReader extends NeoReader[Float] {
        def read(v: java.lang.Object) = v.asInstanceOf[Float]
    }
    implicit object DoubleReader extends NeoReader[Double] {
        def read(v: java.lang.Object) = v.asInstanceOf[Double]
    }
    implicit object CharReader extends NeoReader[Char] {
        def read(v: java.lang.Object) = v.asInstanceOf[Char]
    }
    implicit object StringReader extends NeoReader[String] {
        def read(v: java.lang.Object) = v.asInstanceOf[String]
    }
    implicit def traversableReader[T: NeoReader] = new NeoReader[Traversable[T]] {
        def read(v: java.lang.Object) = v.asInstanceOf[Array[java.lang.Object]].map(elem => implicitly[NeoReader[T]].read(elem)).toTraversable
    }
}
