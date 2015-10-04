package kafka

import com.softwaremill.react.kafka.ReactiveKafka

import kafka.serializer.StringDecoder
import kafka.serializer.StringEncoder

import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber

import scalaz._

import scala.concurrent.Future

object Command {
    type Err[T] = Throwable \/ T

    type Exec[T] = ReaderT[Err, ReactiveKafka, T]

    def apply[T](fn: ReactiveKafka => T): Exec[T] =
        Kleisli[Err, ReactiveKafka, T](kafka => \/.fromTryCatchNonFatal(fn(kafka)))
}
