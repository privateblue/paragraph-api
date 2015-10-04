package api.messaging

import api.base._

import model.base.BlockId

import akka.stream.scaladsl.Sink

import play.api.mvc._
import play.api.libs.json._
import play.api.libs.iteratee.Enumerator
import play.api.libs.streams.Streams
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class MessagingController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    implicit val kafkaSystem = global.kafkaSystem
    implicit val kafkaMaterializer = global.kafkaMaterializer

    def views(blockId: BlockId) =
        eventsOf[model.paragraph.Viewed]("viewed", blockId, _.target)

    def outgoing(blockId: BlockId) =
        eventsOf[model.paragraph.Appended]("appended", blockId, _.target)

    private def eventsOf[T: Format](topic: String, blockId: BlockId, key: T => BlockId) =
        Actions.stream(parse.empty) { (timestamp, _) =>
            val prg = Messages.listen[T, Enumerator[T]](topic) { source =>
                val events = source.filter(e => key(e) == blockId)
                Streams.publisherToEnumerator(events.runWith(Sink.publisher))
            }.program

            Await.result(Program.run(prg, global.env), Duration.Inf)
        }
}
