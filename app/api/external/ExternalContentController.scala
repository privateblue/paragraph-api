package api.external

import api._
import api.base._
import api.paragraph.Graph
import api.messaging.Messages

import model.base._
import model.external.Page
import model.external.Paragraph

import neo._

import play.api.mvc._

import scalaz._
import Scalaz._

class ExternalContentController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    import global.executionContext
    import global.system
    import global.materializer

    def pull = Actions.authenticated { (_, timestamp, body) =>
        val url = (body \ "url").as[String]
        val pageId = PageId(IdGenerator.key)

        val prg = for {
            page <- Pages.parse(url).program
            result <- Graph.pull(timestamp, pageId, page.url, page.author, page.title, page.site).program
            blockIds <- result match {
                case Some(pageId) =>
                    Messages.send("pulled", model.paragraph.Pulled(timestamp, pageId, page.url, page.author, page.title, page.site))
                    addBlocks(timestamp, pageId, page)
                case _ =>
                    Query.error[List[BlockId]](ApiError(409, s"${page.url} has already been downloaded")).program
            }
        } yield blockIds

        Program.run(prg, global.env)
    }

    private def addBlocks(timestamp: Long, pageId: PageId, page: Page): Program[List[BlockId]] = page.paragraphs match {
        case first::rest =>
            val firstId = BlockId(IdGenerator.key)
            val added = Program.noop flatMap {
                _ =>
                    Messages.send("added", model.paragraph.Added(timestamp, pageId, firstId, Some(page.title), Paragraph.blockBody(first))).program
                    Graph.add(timestamp, pageId, firstId, Some(page.title), Paragraph.blockBody(first)).map(List(_)).program
            }
            rest
                .foldLeft(added) {
                    case (previous, paragraph) =>
                        val nextId = BlockId(IdGenerator.key)
                        previous.flatMap {
                            case blockIds =>
                                Messages.send("continued", model.paragraph.Continued(timestamp, nextId, blockIds.head, Paragraph.heading(paragraph), Paragraph.blockBody(paragraph))).program
                                Graph.continue(timestamp, nextId, blockIds.head, Paragraph.heading(paragraph), Paragraph.blockBody(paragraph)).map(_::blockIds).program
                        }
                }
                .map(_.reverse)
        case _ => Query.error(ApiError(500, "Failed to download anything")).program
    }
}
