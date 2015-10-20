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
import play.api.libs.concurrent.Execution.Implicits._

import scalaz._
import Scalaz._

import scala.collection.JavaConversions._

class ReadController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    import api.base.NeoModel._

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

    def incoming(blockId: BlockId) = Actions.public(parse.empty) { (_, _) =>
        val query = for {
            node <- loadBlockNode(blockId)
        } yield parentBlocks(node).sortBy(block => (block.connection.timestamp, block.blockId))
        Program.run(query.program, global.env)
    }

    def outgoing(blockId: BlockId) = Actions.public(parse.empty) { (_, _) =>
        val query = for {
            node <- loadBlockNode(blockId)
        } yield childBlocks(node).sortBy(block => (block.connection.timestamp, block.blockId))
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

    implicit val kafkaSystem = global.kafkaSystem
    implicit val kafkaMaterializer = global.kafkaMaterializer

    def viewed(blockId: BlockId) =
        eventsOf[model.paragraph.Viewed]("viewed", _.target == blockId)

    def appended(blockId: BlockId) =
        eventsOf[model.paragraph.Appended]("appended", _.target == blockId)

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
            val readAuthor = authorOf(node)
            val title = Prop.BlockTitle.from(node).toOption
            (readBlockId |@| readTimestamp |@| readBody |@| readAuthor) {
                case (blockId, timestamp, body, author) =>
                    Block(blockId, title, timestamp, body, author)
            }
        } else ApiError(500, "Cannot convert node to Block").failureNel[Block]

    private def authorOf(node: Node): ValidationNel[Throwable, User] =
        Option(node.getSingleRelationship(Arrow.Author, Direction.INCOMING)) match {
            case Some(authorArrow) => nodeToUser(authorArrow.getStartNode)
            case _ => ApiError(500, "Author not found").failureNel[User]
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

    private def viewsOf(node: Node): List[UserConnection] = for {
        rel <- node.getRelationships(Arrow.View, Direction.INCOMING).toList
        user = rel.getStartNode
        connection <- relationshipToConnection(rel).toOption
        userId <- Prop.UserId.from(user).toOption
        userName <- Prop.UserName.from(user).toOption
    } yield UserConnection(connection, userId, userName)

    private def parentBlocks(node: Node): List[BlockConnection] =
        linkedBlocks(node, Direction.INCOMING)

    private def childBlocks(node: Node): List[BlockConnection] =
        linkedBlocks(node, Direction.OUTGOING)

    private def linkedBlocks(node: Node, dir: Direction): List[BlockConnection] = for {
        rel <- node.getRelationships(Arrow.Link, dir).toList
        other = rel.getOtherNode(node)
        connection <- relationshipToConnection(rel).toOption
        otherId <- Prop.BlockId.from(other).toOption
    } yield BlockConnection(connection, otherId)

    private def relationshipToConnection(rel: Relationship): ValidationNel[Throwable, Connection] = {
        val arrow = neo.Arrow(rel.getType)
        val readUserId = Prop.UserId from rel
        val readTimestamp = Prop.Timestamp from rel
        (readUserId |@| readTimestamp) {
            case (userId, timestamp) =>  Connection(userId, timestamp, arrow)
        }
    }

}
