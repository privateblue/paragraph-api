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

import scala.collection.JavaConverters._

class ExternalContentController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    import api.base.NeoModel._

    import global.executionContext
    import global.system
    import global.materializer

    def pull = Actions.authenticated { (userId, timestamp, body) =>
        val url = (body \ "url").as[String]

        val prg = find(timestamp, userId, url, true)

        Program.run(prg, global.env)
    }

    def resolve = Actions.authenticated { (userId, timestamp, body) =>
        val blockId = (body \ "blockId").as[BlockId]

        val prg = appendExternalLinks(timestamp, userId, blockId)

        Program.run(prg, global.env)
    }

    private def find(timestamp: Long, userId: UserId, url: String, findLinks: Boolean): Program[NonEmptyList[BlockId]] = for {
        page <- Pages.parse(url)
        got <- get(page.url).program
        blockIds <- got match {
            case Nil => create(timestamp, userId, page, findLinks)
            case first::rest => Program.lift(NonEmptyList.nel(first, rest))
        }
    } yield blockIds

    private def get(url: String) = {
        val query = neo"""MATCH (page:${Label.Page} {${Prop.PageUrl =:= url}})-[source:${Arrow.Source}]->(block:${Label.Block})
                          RETURN ${"block" >>: Prop.BlockId}
                          ORDER BY ${"source" >>: Prop.SourceIndex}"""

        def read(result: Result): List[BlockId] =
            if (result.hasNext) {
                val row = result.next().asScala.toMap
                val id = "block" >>: Prop.BlockId from row
                validate(id) :: read(result)
            } else Nil

        Query.result(query)(read)
    }

    private def create(timestamp: Long, userId: UserId, page: Page, findLinks: Boolean): Program[NonEmptyList[BlockId]] = for {
        _ <- Graph.include(timestamp, page.url, page.author, page.title, page.site).program

        zero = Program.lift(List.empty[BlockId])

        linkedBlocks <- page.paragraphs.foldLeft(zero) {
            case (previous, Paragraph.Image(imageUrl)) if page.url != imageUrl => for {
                blockIds <- previous
                blockId <- find(timestamp, userId, imageUrl, false).map(_.head)
                _ <- blockIds.headOption match {
                    case Some(target) => Graph.link(timestamp, userId, target, blockId).program
                    case _ => Program.noop
                }
                _ <- Graph.source(timestamp, page.url, blockId, blockIds.length).program
            } yield blockId::blockIds

            case (previous, Paragraph.Image(imageUrl)) => for {
                blockIds <- previous
                blockId = BlockId(IdGenerator.key)
                body = BlockBody.Image(imageUrl)
                _ <- blockIds.headOption match {
                    case Some(target) => Graph.append(timestamp, userId, blockId, target, body).program
                    case _ => Graph.start(timestamp, userId, blockId, body).program
                }
                _ <- Graph.source(timestamp, page.url, blockId, blockIds.length).program
            } yield blockId::blockIds

            case (previous, Paragraph.Text(content, links)) => for {
                blockIds <- previous
                blockId = BlockId(IdGenerator.key)
                body = BlockBody.Text(content, links)
                _ <- blockIds.headOption match {
                    case Some(target) => Graph.append(timestamp, userId, blockId, target, body).program
                    case _ => Graph.start(timestamp, userId, blockId, body).program
                }
                _ <- if (findLinks) appendExternalLinks(timestamp, userId, blockId)
                     else Program.noop
                _ <- Graph.source(timestamp, page.url, blockId, blockIds.length).program
            } yield blockId::blockIds

            case (previous, Paragraph.Title(text)) => for {
                blockIds <- previous
                blockId = BlockId(IdGenerator.key)
                body = BlockBody.Title(text)
                _ <- blockIds.headOption match {
                    case Some(target) => Graph.append(timestamp, userId, blockId, target, body).program
                    case _ => Graph.start(timestamp, userId, blockId, body).program
                }
                _ <- Graph.source(timestamp, page.url, blockId, blockIds.length).program
            } yield blockId::blockIds
        }
    } yield NonEmptyList.nel(linkedBlocks.head, linkedBlocks.tail).reverse

    private def appendExternalLinks(timestamp: Long, userId: UserId, blockId: BlockId) = for {
        stories <- findExternalLinks(timestamp, userId, blockId)
        _ <- stories.map(story => Graph.link(timestamp, userId, blockId, story.head)).sequenceU.program
    } yield ()

    private def findExternalLinks(timestamp: Long, userId: UserId, blockId: BlockId): Program[List[NonEmptyList[BlockId]]] = for {
        links <- getExternalLinks(blockId).program
        blocks <- links.map {
            url =>
                val found = find(timestamp, userId, url, false).map(url -> _.list)
                Program.recover(found) {
                    case ParseError(_) => url -> List.empty[BlockId]
                }
        }.sequenceU
        linksToKeep = blocks.collect {
            case (url, story) if story.isEmpty => url
        }
        _ <- updateExternalLinks(blockId, linksToKeep).program
        stories = blocks.collect {
            case (url, first::rest) => NonEmptyList.nel(first, rest)
        }
    } yield stories

    private def getExternalLinks(blockId: BlockId) = Query.lift { db =>
        val blockIdProp = Prop.BlockId =:= blockId
        val node = db.findNode(Label.Block, blockIdProp.name, blockIdProp.value)
        Option(node)
            .map(Prop.BlockExternalLinks.from(_).toList.flatten)
            .getOrElse(throw ApiError(404, s"Block $blockId not found"))
    }

    private def updateExternalLinks(blockId: BlockId, links: Traversable[String]) = {
        val query = neo"""MATCH (block:${Label.Block} {${Prop.BlockId =:= blockId}})
                          SET block += {${Prop.BlockExternalLinks =:= links}}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException(s"Block $blockId not found")

        Query.result(query)(read)
    }
}
