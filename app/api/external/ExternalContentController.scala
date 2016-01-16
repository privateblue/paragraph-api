package api.external

import api._
import api.base._
import api.graph.Graph
import api.messaging.Messages

import model.base._
import model.external.Page
import model.external.Paragraph

import neo._

import org.neo4j.graphdb.Result

import play.api.mvc._

import scalaz._
import Scalaz._

import scala.collection.JavaConversions._

class ExternalContentController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    import api.base.NeoModel._

    import global.executionContext
    import global.system
    import global.materializer

    def find = Actions.authenticated { (userId, timestamp, body) =>
        val url = (body \ "url").as[String]
        val pageId = PageId(IdGenerator.key)

        val prg = for {
            page <- Pages.parse(url)
            result <- Graph.download(timestamp, pageId, page.url, page.author, page.title, page.site).program
            blockIds <- result match {
                case Some(pageId) =>
                    Messages.send("downloaded", model.graph.Downloaded(timestamp, pageId, page.url, page.author, page.title, page.site))
                    addBlocks(timestamp, userId, pageId, page)
                case _ =>
                    getBlocks(page.url)
            }
        } yield blockIds

        Program.run(prg, global.env)
    }

    private def addBlocks(timestamp: Long, userId: UserId, pageId: PageId, page: Page): Program[List[BlockId]] = page.paragraphs match {
        case first::rest =>
            val firstId = BlockId(IdGenerator.key)
            val attached = Program.noop flatMap {
                _ =>
                    Messages.send("attached", model.graph.Attached(timestamp, userId, pageId, firstId, Paragraph.blockBody(first))).program
                    Graph.attach(timestamp, userId, pageId, firstId, Paragraph.blockBody(first)).map(List(_)).program
            }
            rest
                .foldLeft(attached) {
                    case (previous, paragraph) =>
                        val nextId = BlockId(IdGenerator.key)
                        previous.flatMap {
                            case blockIds =>
                                Messages.send("continued", model.graph.Continued(timestamp, userId, pageId, nextId, blockIds.head, Paragraph.blockBody(paragraph))).program
                                Graph.continue(timestamp, userId, pageId, nextId, blockIds.head, Paragraph.blockBody(paragraph)).map(_ => nextId::blockIds).program
                        }
                }
                .map(_.reverse)
        case _ => Query.error(ApiError(500, "Failed to download anything")).program
    }

    private def getBlocks(url: String): Program[List[BlockId]] = {
        val query = neo"""MATCH (page:${Label.Page} {${Prop.PageUrl =:= url}})-[source:${Arrow.Source}]->(block:${Label.Block})
                          RETURN ${"block" >>: Prop.BlockId}
                          ORDER BY ${"source" >>: Prop.SourceIndex}"""

        def read(result: Result): List[BlockId] =
            if (result.hasNext) {
                val row = result.next().toMap
                val id = "block" >>: Prop.BlockId from row
                validate(id) :: read(result)
            } else Nil

        Query.result(query)(read).program
    }
}
