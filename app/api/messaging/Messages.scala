package api.messaging

import api.base.IdGenerator

import kafka.Command

import com.softwaremill.react.kafka.ProducerProperties
import com.softwaremill.react.kafka.ConsumerProperties

import kafka.serializer.StringEncoder
import kafka.serializer.StringDecoder

import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

import akka.actor.ActorSystem
import akka.stream.Materializer

import play.api.libs.json._

import scala.concurrent.Future
import scala.concurrent.Promise

object Messages {
    def send[T](topic: String, msg: T)(implicit writes: Writes[T], system: ActorSystem, mat: Materializer) = Command { kafka =>
        val subscriber = kafka.publish(ProducerProperties(
            brokerList = kafka.host,
            topic = topic,
            encoder = new StringEncoder
        ))
        val message = writes.writes(msg).toString
        Source.single[String](message).to(Sink(subscriber)).run()
    }

    def noop = Command(_ => ())

    def listen[T, R](topic: String)(fn: Source[T, _] => R)(implicit reads: Reads[T], system: ActorSystem) = Command { kafka =>
        val publisher = kafka.consume(ConsumerProperties(
            brokerList = kafka.host,
            zooKeeperHost = kafka.zooKeeperHost,
            topic = topic,
            groupId = IdGenerator.key,
            decoder = new StringDecoder
        ))
        val src = Source(publisher).map(msg => Json.parse(msg.message).as[T])
        fn(src)
    }
}
