package kafka

import kafka.producer.ProducerConfig
import kafka.producer.Producer
import kafka.consumer.Consumer
import kafka.consumer.ConsumerConfig

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringSerializer

import org.slf4j.Logger

import scalaz._

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext

case class Env(
    zkConnect: String,
    brokers: String,
    logger: Logger)(implicit ec: ExecutionContext) {

    private val producerConfig = {
        val props = new java.util.Properties
        props.setProperty("bootstrap.servers", brokers)
        props
    }

    private val producer = new KafkaProducer[String, String](producerConfig, new StringSerializer, new StringSerializer)

    private val consumerConfig = {
        val props = new java.util.Properties
        props.setProperty("zookeeper.connect", zkConnect)
        props
    }

    def run[T](out: Command.Out[T]): Future[T] =
        out(producer).recover {
            case e: Throwable =>
                logger.error(e.getMessage)
                throw e
        }

    def run[T](in: Command.In[T]): T =
        in(consumerConfig) match {
            case -\/(e) =>
                logger.error(e.getMessage)
                throw e // TODO there has to be a better way :)
            case \/-(res) => res
        }

    def shutdown(): Future[Unit] = Future {
        producer.close
    }
}
