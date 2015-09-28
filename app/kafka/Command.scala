package kafka

import kafka.consumer.Consumer
import kafka.consumer.ConsumerConnector
import kafka.consumer.ConsumerConfig

import org.apache.kafka.clients.producer.KafkaProducer

import scalaz._

import scala.concurrent.Future

object Command {
    type Out[T] = ReaderT[Future, KafkaProducer[String, String], T]

    def send[T](fn: KafkaProducer[String, String] => Future[T]): Out[T] =
        Kleisli[Future, KafkaProducer[String, String], T](fn)

    type Err[T] = Throwable \/ T

    type In[T] = ReaderT[Err, java.util.Properties, T]

    def receive[T](groupId: String)(fn: ConsumerConnector => T): In[T] =
        Kleisli[Err, java.util.Properties, T] { props =>
            \/.fromTryCatchNonFatal {
                props.setProperty("group.id", groupId)
                val config = new ConsumerConfig(props)
                val consumer = Consumer.create(config)
                fn(consumer)
            }
        }
}
