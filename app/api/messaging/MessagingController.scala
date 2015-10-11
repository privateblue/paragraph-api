package api.messaging

import api.base._

import model.base.BlockId

import akka.stream.scaladsl.Sink

import play.api.mvc._
import play.api.mvc.WebSocket.FrameFormatter
import play.api.libs.json._
import play.api.libs.iteratee.Iteratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.streams.Streams
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class MessagingController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    implicit val kafkaSystem = global.kafkaSystem
    implicit val kafkaMaterializer = global.kafkaMaterializer

    def viewed(blockId: BlockId) =
        eventsOf[model.paragraph.Viewed]("viewed", blockId, _.target)

    def appended(blockId: BlockId) =
        eventsOf[model.paragraph.Appended]("appended", blockId, _.target)

    private def eventsOf[T: Format : FrameFormatter](topic: String, blockId: BlockId, key: T => BlockId) =
        WebSocket.using[T] { request =>
            val in = Iteratee.ignore[T]
            val prg = Messages.listen[T, (Iteratee[T, _], Enumerator[T])](topic) { source =>
                val events = source.filter(e => key(e) == blockId)
                val out = Streams.publisherToEnumerator(events.runWith(Sink.publisher))
                (in, out)
            }.program
            Await.result(Program.run(prg, global.env), Duration.Inf)
        }
}
