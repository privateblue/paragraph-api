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

    def pull = Actions.authenticated { (userId, timestamp, body) =>
        val url = (body \ "url").as[String]

        val prg = find(timestamp, userId, url)

        Program.run(prg, global.env)
    }

    private def find(timestamp: Long, userId: UserId, url: String): Program[NonEmptyList[BlockId]] = for {
        page <- Pages.parse(url)
        got <- get(page.url).program
        blockIds <- got match {
            case Nil => create(timestamp, userId, page)
            case first::rest => Program.lift(NonEmptyList.nel(first, rest))
        }
    } yield blockIds

    private def get(url: String) = {
        val query = neo"""MATCH (page:${Label.Page} {${Prop.PageUrl =:= url}})-[source:${Arrow.Source}]->(block:${Label.Block})
                          RETURN ${"block" >>: Prop.BlockId}
                          ORDER BY ${"source" >>: Prop.SourceIndex}"""

        def read(result: Result): List[BlockId] =
            if (result.hasNext) {
                val row = result.next().toMap
                val id = "block" >>: Prop.BlockId from row
                validate(id) :: read(result)
            } else Nil

        Query.result(query)(read)
    }

    private def create(timestamp: Long, userId: UserId, page: Page): Program[NonEmptyList[BlockId]] = for {
        _ <- Graph.include(timestamp, page.url, page.author, page.title, page.site).program

        blocks = page.paragraphs.map {
            case Paragraph.Image(imageUrl, links) if page.url != imageUrl => find(timestamp, userId, imageUrl).map(_.head)
            case paragraph => Graph.start(timestamp, userId, BlockId(IdGenerator.key), Paragraph.blockBody(paragraph)).program
        }

        zero = Program.lift(List.empty[BlockId])
        linkedBlocks <- blocks.foldLeft(zero) {
            (previous, block) =>
                for {
                    blockIds <- previous
                    blockId <- block
                    _ <- blockIds.headOption match {
                        case Some(target) => Graph.link(timestamp, userId, target, blockId).program
                        case _ => Program.noop
                    }
                    _ <- Graph.source(timestamp, page.url, blockId, blockIds.length).program
                } yield blockId::blockIds
        }
    } yield NonEmptyList.nel(linkedBlocks.head, linkedBlocks.tail).reverse
}
