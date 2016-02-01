package neo

import scala.collection.JavaConverters._

trait NeoWriter[-T] { self =>
    def write(v: T): java.lang.Object
    def contramap[U](fn: U => T): NeoWriter[U] =  new NeoWriter[U] {
        def write(v: U) = self.write(fn(v))
    }
}

object NeoWriter {
    implicit object BooleanWriter extends NeoWriter[Boolean] {
        def write(v: Boolean) = v.asInstanceOf[java.lang.Object]
    }
    implicit object ByteWriter extends NeoWriter[Byte] {
        def write(v: Byte) = v.asInstanceOf[java.lang.Object]
    }
    implicit object ShortWriter extends NeoWriter[Short] {
        def write(v: Short) = v.asInstanceOf[java.lang.Object]
    }
    implicit object IntWriter extends NeoWriter[Int] {
        def write(v: Int) = v.asInstanceOf[java.lang.Object]
    }
    implicit object LongWriter extends NeoWriter[Long] {
        def write(v: Long) = v.asInstanceOf[java.lang.Object]
    }
    implicit object FloatWriter extends NeoWriter[Float] {
        def write(v: Float) = v.asInstanceOf[java.lang.Object]
    }
    implicit object DoubleWriter extends NeoWriter[Double] {
        def write(v: Double) = v.asInstanceOf[java.lang.Object]
    }
    implicit object CharWriter extends NeoWriter[Char] {
        def write(v: Char) = v.asInstanceOf[java.lang.Object]
    }
    implicit object StringWriter extends NeoWriter[String] {
        def write(v: String) = v
    }
    implicit def traversableWriter[T: NeoWriter] = new NeoWriter[Traversable[T]] {
        def write(v: Traversable[T]) = v.map(implicitly[NeoWriter[T]].write(_)).toArray
    }
}
