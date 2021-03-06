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

    def pull = Actions.public { (timestamp, body) =>
        val url = (body \ "url").as[String]

        val prg = find(timestamp, url, true)

        Program.run(prg, global.env)
    }

    def resolve = Actions.public { (timestamp, body) =>
        val blockId = (body \ "blockId").as[BlockId]

        val prg = appendExternalLinks(timestamp, blockId)

        Program.run(prg, global.env)
    }

    private def find(timestamp: Long, url: String, resolveLinks: Boolean): Program[NonEmptyList[BlockId]] = for {
        page <- Pages.parse(url)
        got <- get(page.url).program
        blockIds <- got match {
            case Nil => create(timestamp, page, resolveLinks)
            case first::rest => Program.lift(NonEmptyList.nel(first, rest))
        }
    } yield blockIds

    private def get(url: String) = {
        val query = neo"""MATCH (page:${Label.Page} ${l(Prop.PageUrl =:= url)})-[source:${Arrow.Source}]->(block:${Label.Block})
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

    private def create(timestamp: Long, page: Page, resolveLinks: Boolean): Program[NonEmptyList[BlockId]] = for {
        _ <- Graph.include(timestamp, page.url, page.author, page.title, page.site, page.published).program
        _ <- Messages.send("included", model.graph.Included(timestamp, page.url, page.author, page.title, page.site, page.published)).program

        zero = Program.lift(List.empty[BlockId])

        linkedBlocks <- page.paragraphs.foldLeft(zero) {
            (previous, paragraph) => previous.flatMap(blockIds => createBlock(timestamp, page.url, blockIds, paragraph, resolveLinks))
        }
    } yield NonEmptyList.nel(linkedBlocks.head, linkedBlocks.tail).reverse

    private def createBlock(timestamp: Long, url: String, blockIds: List[BlockId], paragraph: Paragraph, resolveLinks: Boolean): Program[List[BlockId]] = for {
        blockId <- paragraph match {
            case Paragraph.Image(imageUrl) if url != imageUrl =>
                find(timestamp, imageUrl, false).map(_.head)
            case _ =>
                Program.lift(BlockId(IdGenerator.key))
        }

        body = Paragraph.blockBody(paragraph)

        _ <- blockIds.headOption.map { target =>
            paragraph match {
                case Paragraph.Image(imageUrl) if url != imageUrl => for {
                    _ <- Graph.link(timestamp, None, target, blockId).program
                    _ <- Messages.send("linked", model.graph.Linked(timestamp, None, target, blockId)).program
                } yield ()
                case _ => for {
                    _ <- Graph.append(timestamp, None, blockId, target, body).program
                    _ <- Messages.send("appended", model.graph.Appended(timestamp, None, blockId, target, body)).program
                } yield ()
            }
        }.getOrElse {
            paragraph match {
                case Paragraph.Image(imageUrl) if url != imageUrl => Program.noop
                case _ => for {
                    _ <- Graph.start(timestamp, None, blockId, body).program
                    _ <- Messages.send("started", model.graph.Started(timestamp, None, blockId, body)).program
                } yield ()
            }
        }

        _ <- Graph.source(timestamp, url, blockId, blockIds.length).program

        _ <- Messages.send("sourced", model.graph.Sourced(timestamp, url, blockId, blockIds.length)).program

        _ <- paragraph match {
            case Paragraph.Text(_, _) if (resolveLinks) => appendExternalLinks(timestamp, blockId)
            case _ => Program.noop
        }
    } yield blockId::blockIds

    private def appendExternalLinks(timestamp: Long, blockId: BlockId) = for {
        stories <- findExternalLinks(timestamp, blockId)
        _ <- stories.map(story => Graph.link(timestamp, None, blockId, story.head)).sequenceU.program
        _ <- stories.map(story => Messages.send("linked", model.graph.Linked(timestamp, None, blockId, story.head))).sequenceU.program
    } yield ()

    private def findExternalLinks(timestamp: Long, blockId: BlockId): Program[List[NonEmptyList[BlockId]]] = for {
        body <- getBlockBody(blockId).program
        stories <- body match {
            case b @ BlockBody.Text(_, _) => resolveExternalLinks(timestamp, blockId, b)
            case _ => Program.lift(List.empty[NonEmptyList[BlockId]])
        }
    } yield stories

    private def resolveExternalLinks(timestamp: Long, blockId: BlockId, body: BlockBody.Text): Program[List[NonEmptyList[BlockId]]] = for {
        blocks <- body.externalLinks.map {
            url =>
                val found = find(timestamp, url, false).map(url -> _.list)
                Program.recover(found) {
                    case ParseError(_) => url -> List.empty[BlockId]
                }
        }.sequenceU
        linksToKeep = blocks.collect {
            case (url, story) if story.isEmpty => url
        }
        _ <- Graph.edit(timestamp, blockId, BlockBody.Text(body.text, linksToKeep)).program
        _ <- Messages.send("edited", model.graph.Edited(timestamp, blockId, BlockBody.Text(body.text, linksToKeep))).program
        stories = blocks.collect {
            case (url, first::rest) => NonEmptyList.nel(first, rest)
        }
    } yield stories

    private def getBlockBody(blockId: BlockId) = Query.lift { db =>
        val blockIdProp = Prop.BlockId =:= blockId
        val node = db.findNode(Label.Block, blockIdProp.name, blockIdProp.value)
        Option(node)
            .map(PropertyValue.as[BlockBody](_).toOption)
            .flatten
            .getOrElse(throw ApiError(404, s"Block $blockId not found"))
    }
}
