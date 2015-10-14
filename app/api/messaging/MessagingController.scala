package api.messaging

import api.base._

import model.base.BlockId

import akka.stream.scaladsl.Source

import play.api.mvc._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._

class MessagingController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    implicit val kafkaSystem = global.kafkaSystem
    implicit val kafkaMaterializer = global.kafkaMaterializer

    def viewed(blockId: BlockId) =
        eventsOf[model.paragraph.Viewed]("viewed", blockId, _.target)

    def appended(blockId: BlockId) =
        eventsOf[model.paragraph.Appended]("appended", blockId, _.target)

    private def eventsOf[T: Format](topic: String, blockId: BlockId, key: T => BlockId) =
        Actions.socket[T] {
            val prg = Messages.listen[T, Source[T, _]](topic) { source =>
                source.filter(e => key(e) == blockId)
            }.program
            Program.run(prg, global.env)
        }
}
