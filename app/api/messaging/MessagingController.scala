package api.messaging

import api.base._

import model.base.BlockId

import play.api.mvc._
import play.api.mvc.WebSocket.FrameFormatter
import play.api.libs.json._
import play.api.libs.iteratee.Enumerator
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class MessagingController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    def views(blockId: BlockId) = eventsOf[model.paragraph.Viewed]("viewed", blockId, _.target)

    def outgoing(blockId: BlockId) = eventsOf[model.paragraph.Appended]("appended", blockId, _.target)

    private def eventsOf[T: Format](topic: String, blockId: BlockId, key: T => BlockId) =
        Actions.stream(parse.empty) { (timestamp, _) =>
            // val prg = Messages.listen[T, Enumerator[T]](topic) { (consumer, stream) =>
            //     val events = stream.filter(e => key(e) == blockId)
            //     Enumerator.unfold(events) {
            //         case Stream.Empty => None
            //         case head#::tail => Some(tail, head)
            //     }.onDoneEnumerating(consumer.shutdown())
            // }.program
            //
            // Await.result(Program.run(prg, global.env), Duration.Inf)


            val consumerConfig = new kafka.consumer.ConsumerConfig({
                val props = new java.util.Properties
                props.setProperty("zookeeper.connect", global.config.zkConnect)
                props.setProperty("group.id", api.base.IdGenerator.key)
                props
            })
            val consumer = kafka.consumer.Consumer.create(consumerConfig)
            val map = consumer.createMessageStreams(Map(topic -> 1), new kafka.serializer.StringDecoder, new kafka.serializer.StringDecoder)
            def stream: Stream[T] = map.get(topic).map(_.headOption).flatten match {
                case None => Stream.Empty
                case Some(ks) if ks.isEmpty => Stream.Empty
                case Some(ks) =>
                    val v = Json.parse(ks.head.message).as[T]
                    println
                    println(v)
                    Stream.cons(v, stream)
            }
            // val stream = consumer.createMessageStreamsByFilter(new kafka.consumer.Whitelist(topic), 1, new kafka.serializer.StringDecoder, new kafka.serializer.StringDecoder)
            //     .headOption
            //     .map(_.toIterator)
            //     .getOrElse(Iterator.empty)
            //     .map(msg => Json.parse(msg.message).as[T])
            val events = stream.filter(e => key(e) == blockId)
            // val events: Stream[Int] = {
            //     def loop(v: Int): Stream[Int] = v #:: loop(v + 1)
            //     loop(0)
            // }
            Enumerator.enumerate(events).onDoneEnumerating{ println("END"); consumer.shutdown() }
        }
}
