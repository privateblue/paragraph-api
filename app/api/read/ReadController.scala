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

import scala.collection.JavaConverters._

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

    def sources(blockId: BlockId) = Actions.public(parse.empty) { (_, _) =>
        val query = for {
            node <- loadBlockNode(blockId)
        } yield sourcesOf(node)
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
                    val outgoing = path.head.getRelationships(Arrow.Link, Direction.OUTGOING).asScala
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
        eventsOf[model.graph.Viewed]("viewed", _.target == blockId)

    def appended(blockId: BlockId) =
        eventsOf[model.graph.Appended]("appended", _.target == blockId)

    def prepended(blockId: BlockId) =
        eventsOf[model.graph.Prepended]("prepended", _.target == blockId)

    def linked(blockId: BlockId) =
        eventsOf[model.graph.Linked]("linked", (lnk => lnk.from == blockId || lnk.to == blockId))

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
        val blockIdProp = Prop.BlockId =:= blockId
        val node = db.findNode(Label.Block, blockIdProp.name, blockIdProp.value)
        Option(node).getOrElse(throw ApiError(404, s"Block $blockId not found"))
    }

    private def nodeToBlock(node: Node): ValidationNel[Throwable, Block] =
        if (node.getLabels.asScala.toSeq.contains(Label.Block)) {
            val readBlockId = Prop.BlockId from node
            val readTimestamp = Prop.Timestamp from node
            val readModified = Prop.BlockModified from node
            val readBody = PropertyValue.as[BlockBody](node)
            val author = authorOf(node).toOption
            val sources = sourcesOf(node).sortBy(_.timestamp)
            (readBlockId |@| readTimestamp |@| readModified |@| readBody) {
                case (blockId, timestamp, modified, body) =>
                    Block(blockId, timestamp, modified, body, author, sources)
            }
        } else ApiError(500, "Cannot convert node to Block").failureNel[Block]

    private def authorOf(node: Node): ValidationNel[Throwable, User] =
        Option(node.getSingleRelationship(Arrow.Author, Direction.INCOMING)) match {
            case Some(authorArrow) => nodeToUser(authorArrow.getStartNode)
            case _ => ApiError(404, "Author not found").failureNel[User]
        }

    private def nodeToUser(node: Node): ValidationNel[Throwable, User] =
        if (node.getLabels.asScala.toSeq.contains(Label.User)) {
            val readUserId = Prop.UserId from node
            val readTimestamp = Prop.Timestamp from node
            val readForeignId = Prop.UserForeignId from node
            val readName = Prop.UserName from node
            (readUserId |@| readTimestamp |@| readForeignId |@| readName) {
                case (userId, timestamp, foreignId, name) => User(userId, timestamp, foreignId, name)
            }
        } else ApiError(500, "Cannot convert node to User").failureNel[User]

    private def sourcesOf(node: Node): List[Page] = for {
        rel <- node.getRelationships(Arrow.Source, Direction.INCOMING).asScala.toList
        page = rel.getStartNode
        timestamp <- Prop.Timestamp.from(page).toOption
        url <- Prop.PageUrl.from(page).toOption
        author = Prop.PageAuthor.from(page).toOption
        title = Prop.PageTitle.from(page).toOption
        site = Prop.PageSite.from(page).toOption
        published = Prop.PagePublished.from(page).toOption
    } yield Page(timestamp, url, author, title, site, published)

    private def viewsOf(node: Node): List[Author] = for {
        rel <- node.getRelationships(Arrow.View, Direction.INCOMING).asScala.toList
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
        rel <- node.getRelationships(Arrow.Link, dir).asScala.toList
        other = rel.getOtherNode(node)
        timestamp <- Prop.Timestamp.from(rel).toOption
        userId = Prop.UserId.from(rel).toOption
        otherId <- Prop.BlockId.from(other).toOption
    } yield Link(timestamp, otherId, userId)

}
