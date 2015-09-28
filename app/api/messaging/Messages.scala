package api.messaging

import api.base.IdGenerator

import kafka.Command

import kafka.consumer.ConsumerConnector
import kafka.consumer.Whitelist
import kafka.serializer.StringDecoder

import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.clients.producer.Callback

import play.api.libs.json._

import scala.concurrent.Future
import scala.concurrent.Promise

object Messages {
    def send[T](topic: String, msg: T)(implicit writes: Writes[T]) = Command.send { producer =>
        val promise = Promise[RecordMetadata]
        val message = writes.writes(msg).toString
        val kafkaMsg = new ProducerRecord[String, String](topic, message)
        producer.send(kafkaMsg, new Callback {
            def onCompletion(metadata: RecordMetadata, exception: Exception) =
                if (metadata != null) promise success metadata
                else promise failure exception
        })
        promise.future
    }

    def noop = Command.send(_ => Future.successful(()))

    def listen[T, R](topic: String)(fn: (ConsumerConnector, Stream[T]) => R)(implicit reads: Reads[T]) = Command.receive(IdGenerator.key) { consumer =>
        val stream = consumer.createMessageStreamsByFilter(new Whitelist(topic), 1, new StringDecoder, new StringDecoder)
            .headOption
            .map(_.toStream)
            .getOrElse(Stream.empty)
            .map(msg => Json.parse(msg.message).as[T])
        fn(consumer, stream)
    }
}
