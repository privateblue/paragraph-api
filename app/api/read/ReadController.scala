package api.read

import api.base._
import api.messaging.Messages

import model.base._
import model.read._

import neo._

import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Relationship
import org.neo4j.graphdb.Direction

import akka.stream.scaladsl.Source

import play.api.mvc._
import play.api.libs.json._

import scalaz._
import Scalaz._

import scala.collection.JavaConversions._

class ReadController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    import api.base.NeoModel._

    import global.executionContext
    import global.system
    import global.materializer

    def block(blockId: BlockId) = Actions.public(parse.empty) { (_, _) =>
        val query = loadBlock(blockId)
        Program.run(query.program, global.env)
    }

    def author(blockId: BlockId) = Actions.public(parse.empty) { (_, _) =>
        val query = for {
            node <- loadBlockNode(blockId)
        } yield validate(authorOf(node))
        Program.run(query.program, global.env)
    }

    def source(blockId: BlockId) = Actions.public(parse.empty) { (_, _) =>
        val query = for {
            node <- loadBlockNode(blockId)
        } yield validate(sourceOf(node))
        Program.run(query.program, global.env)
    }

    def incoming(blockId: BlockId) = Actions.public(parse.empty) { (_, _) =>
        val query = for {
            node <- loadBlockNode(blockId)
        } yield parentBlocks(node).sortBy(link => (link.timestamp, link.blockId))
        Program.run(query.program, global.env)
    }

    def outgoing(blockId: BlockId) = Actions.public(parse.empty) { (_, _) =>
        val query = for {
            node <- loadBlockNode(blockId)
        } yield childBlocks(node).sortBy(link => (link.timestamp, link.blockId))
        Program.run(query.program, global.env)
    }

    def views(blockId: BlockId) = Actions.public(parse.empty) { (_, _) =>
        val query = for {
            node <- loadBlockNode(blockId)
        } yield viewsOf(node)
        Program.run(query.program, global.env)
    }

    def path(blockIds: Seq[BlockId]) = Actions.public(parse.empty) { (_, _) =>
        val query = blockIds.toList match {
            case Nil => Query.error(ApiError(400, "Specify at least one block id parameter"))
            case firstId :: restIds => for {
                first <- loadBlockNode(firstId)
                nodes = restIds.foldLeft(List(first)) { (path, id) =>
                    val outgoing = path.head.getRelationships(Arrow.Link, Direction.OUTGOING)
                    val node = outgoing.map(_.getEndNode).find(n => Prop.BlockId.from(n).toOption.contains(id))
                    val block = node.getOrElse(throw ApiError(404, s"Block $id not found"))
                    block :: path
                }
                blocks = nodes.foldLeft(List.empty[Block]) { (path, node) =>
                    val extract = nodeToBlock _ andThen validate _
                    extract(node) :: path
                }
            } yield blocks
        }
        Program.run(query.program, global.env)
    }

    def viewed(blockId: BlockId) =
        eventsOf[model.paragraph.Viewed]("viewed", _.target == blockId)

    def appended(blockId: BlockId) =
        eventsOf[model.paragraph.Appended]("appended", _.target == blockId)

    def prepended(blockId: BlockId) =
        eventsOf[model.paragraph.Prepended]("prepended", _.target == blockId)

    def linked(blockId: BlockId) =
        eventsOf[model.paragraph.Linked]("linked", (lnk => lnk.from == blockId || lnk.to == blockId))

    private def eventsOf[T: Format](topic: String, predicate: T => Boolean) =
        Actions.publicSocket[T] {
            val prg = Messages.listen[T, Source[T, _]](topic) { source =>
                source.filter(predicate)
            }.program
            Program.run(prg, global.env)
        }

    private def loadBlock(blockId: BlockId): Query.Exec[Block] =
        loadBlockNode(blockId).map(nodeToBlock _ andThen validate _)

    private def loadBlockNode(blockId: BlockId): Query.Exec[Node] = Query.lift { db =>
        val node = db.findNode(Label.Block, Prop.BlockId.name, NeoValue.toNeo(blockId))
        Option(node).getOrElse(throw ApiError(404, s"Block $blockId not found"))
    }

    private def nodeToBlock(node: Node): ValidationNel[Throwable, Block] =
        if (node.getLabels.toSeq.contains(Label.Block)) {
            val readBlockId = Prop.BlockId from node
            val readTimestamp = Prop.Timestamp from node
            val readBody = Prop.BlockBodyLabel from node match {
                case Success(BlockBody.Label.text) => Prop.TextBody from node
                case Success(BlockBody.Label.image) => Prop.ImageBody from node
                case _ => ApiError(500, "Invalid body type").failureNel[BlockBody]
            }
            val author = authorOf(node).toOption
            val source = sourceOf(node).toOption
            val title = Prop.BlockTitle.from(node).toOption
            (readBlockId |@| readTimestamp |@| readBody) {
                case (blockId, timestamp, body) =>
                    Block(blockId, title, timestamp, body, author, source)
            }
        } else ApiError(500, "Cannot convert node to Block").failureNel[Block]

    private def authorOf(node: Node): ValidationNel[Throwable, User] =
        Option(node.getSingleRelationship(Arrow.Author, Direction.INCOMING)) match {
            case Some(authorArrow) => nodeToUser(authorArrow.getStartNode)
            case _ => ApiError(404, "Author not found").failureNel[User]
        }

    private def nodeToUser(node: Node): ValidationNel[Throwable, User] =
        if (node.getLabels.toSeq.contains(Label.User)) {
            val readUserId = Prop.UserId from node
            val readTimestamp = Prop.Timestamp from node
            val readForeignId = Prop.UserForeignId from node
            val readName = Prop.UserName from node
            (readUserId |@| readTimestamp |@| readForeignId |@| readName) {
                case (userId, timestamp, foreignId, name) => User(userId, timestamp, foreignId, name)
            }
        } else ApiError(500, "Cannot convert node to User").failureNel[User]

    private def sourceOf(node: Node): ValidationNel[Throwable, Page] =
        Option(node.getSingleRelationship(Arrow.Source, Direction.INCOMING)) match {
            case Some(sourceArrow) => nodeToPage(sourceArrow.getStartNode)
            case _ => ApiError(404, "Source not found").failureNel[Page]
        }

    private def nodeToPage(node: Node): ValidationNel[Throwable, Page] =
        if (node.getLabels.toSeq.contains(Label.Page)) {
            val readPageId = Prop.PageId from node
            val readTimestamp = Prop.Timestamp from node
            val readUrl = Prop.PageUrl from node
            val readAuthor = Prop.PageAuthor from node
            val readTitle = Prop.PageTitle from node
            val readSite = Prop.PageSite from node
            (readPageId |@| readTimestamp |@| readUrl |@| readAuthor |@| readTitle |@| readSite) {
                case (pageId, timestamp, url, author, title, site) => Page(pageId, timestamp, url, author, title, site)
            }
        } else ApiError(500, "Cannot convert node to Page").failureNel[Page]

    private def viewsOf(node: Node): List[Author] = for {
        rel <- node.getRelationships(Arrow.View, Direction.INCOMING).toList
        user = rel.getStartNode
        timestamp <- Prop.Timestamp.from(rel).toOption
        userId <- Prop.UserId.from(user).toOption
        userName <- Prop.UserName.from(user).toOption
    } yield Author(timestamp, userId, userName)

    private def parentBlocks(node: Node): List[Link] =
        linkedBlocks(node, Direction.INCOMING)

    private def childBlocks(node: Node): List[Link] =
        linkedBlocks(node, Direction.OUTGOING)

    private def linkedBlocks(node: Node, dir: Direction): List[Link] = for {
        rel <- node.getRelationships(Arrow.Link, dir).toList
        other = rel.getOtherNode(node)
        timestamp <- Prop.Timestamp.from(rel).toOption
        userId = Prop.UserId.from(rel).toOption
        otherId <- Prop.BlockId.from(other).toOption
    } yield Link(timestamp, otherId, userId)

}
