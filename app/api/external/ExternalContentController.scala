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

    def pull = Actions.authenticated { (_, timestamp, body) =>
        val url = (body \ "url").as[String]
        val pageId = PageId(IdGenerator.key)

        val prg = for {
            page <- Pages.parse(url).program
            result <- Graph.pull(timestamp, pageId, page.url, page.author, page.title, page.site).program
            blockIds <- result match {
                case Some(pageId) =>
                    Messages.send("pulled", model.graph.Pulled(timestamp, pageId, page.url, page.author, page.title, page.site))
                    addBlocks(timestamp, pageId, page)
                case _ =>
                    getBlocks(page.url)
            }
        } yield blockIds

        Program.run(prg, global.env)
    }

    private def addBlocks(timestamp: Long, pageId: PageId, page: Page): Program[List[BlockId]] = page.paragraphs match {
        case first::rest =>
            val firstId = BlockId(IdGenerator.key)
            val added = Program.noop flatMap {
                _ =>
                    Messages.send("added", model.graph.Added(timestamp, pageId, firstId, Some(page.title), Paragraph.blockBody(first))).program
                    Graph.add(timestamp, pageId, firstId, Some(page.title), Paragraph.blockBody(first)).map(List(_)).program
            }
            rest
                .foldLeft(added) {
                    case (previous, paragraph) =>
                        val nextId = BlockId(IdGenerator.key)
                        previous.flatMap {
                            case blockIds =>
                                Messages.send("continued", model.graph.Continued(timestamp, nextId, blockIds.head, Paragraph.heading(paragraph), Paragraph.blockBody(paragraph))).program
                                Graph.continue(timestamp, nextId, blockIds.head, Paragraph.heading(paragraph), Paragraph.blockBody(paragraph)).map(_::blockIds).program
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
