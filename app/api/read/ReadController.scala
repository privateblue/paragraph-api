package api.read

import api.base._

import model.base._
import model.read._

import neo._

import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Relationship
import org.neo4j.graphdb.Direction

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

import scalaz._

import scala.collection.JavaConversions._

class ReadController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    import api.base.NeoModel._

    def path(blockIds: Seq[BlockId]) = Actions.public(parse.empty) { (_, _) =>
        val query = blockIds.toList match {
            case Nil => Query.error(ApiError(400, "Specify at least one block id parameter"))
            case firstId :: restIds => for {
                first <- loadNode(firstId)
                nodes = restIds.foldLeft(List(first)) { (path, id) =>
                    val outgoing = path.head.getRelationships(Arrow.Link, Direction.OUTGOING)
                    val node = outgoing.map(_.getEndNode).find(node => Prop.BlockId.from(node).contains(id))
                    val block = node.getOrElse(throw ApiError(404, s"Block $id not found"))
                    block :: path
                }
                blocks = nodes.foldLeft(List.empty[Block]) { (path, node) =>
                    val block = nodeToBlock(node).getOrElse(throw ApiError(404, "Missing block properties"))
                    block :: path
                }
            } yield blocks
        }
        Program.run(query.program, global.env)
    }

    private def loadBlock(blockId: BlockId) =
        loadNode(blockId).map(node => nodeToBlock(node).getOrElse(throw ApiError(404, "missing block properties")))

    private def loadNode(blockId: BlockId) = Query.lift { db =>
        val node = db.findNode(Label.Block, Prop.BlockId.name, NeoValue(blockId).underlying)
        Option(node).getOrElse(throw ApiError(404, s"Block $blockId not found"))
    }

    private def nodeToBlock(node: Node): Option[Block] =
        if (node.getLabels.toSeq.contains(Label.Block))
            for {
                blockId <- Prop.BlockId from node
                title = Prop.BlockTitle from node
                timestamp <- Prop.Timestamp from node
                bodyLabel <- Prop.BlockBodyLabel from node
                body <- bodyLabel match {
                    case BlockBody.Label.text => Prop.TextBody from node
                    case BlockBody.Label.image => Prop.ImageBody from node
                }
                authorArrow <- Option(node.getSingleRelationship(Arrow.Author, Direction.INCOMING))
                author <- nodeToUser(authorArrow.getStartNode)
                incoming = parentBlocks(node)
                outgoing = childBlocks(node)
                views = node.getRelationships(Direction.INCOMING, Arrow.View).flatMap(relationshipToConnection).toSeq
            } yield Block(blockId, title, timestamp, body, author, incoming, outgoing, views)
        else None

    private def nodeToUser(node: Node): Option[User] =
        if (node.getLabels.toSeq.contains(Label.User))
            for {
                userId <- Prop.UserId from node
                timestamp <- Prop.Timestamp from node
                foreignId <- Prop.UserForeignId from node
                name <- Prop.UserName from node
            } yield User(userId, timestamp, foreignId, name)
        else None

    private def parentBlocks(node: Node): Seq[BlockConnection] =
        connectingBlocks(node, Direction.INCOMING)

    private def childBlocks(node: Node): Seq[BlockConnection] =
        connectingBlocks(node, Direction.OUTGOING)

    private def connectingBlocks(node: Node, dir: Direction): Seq[BlockConnection] = for {
        rel <- node.getRelationships(dir).filter(_.getOtherNode(node).getLabels.toSeq.contains(Label.Block)).toSeq
        connection <- relationshipToConnection(rel)
        other = rel.getOtherNode(node)
        otherId <- Prop.BlockId from other
    } yield BlockConnection(connection, otherId)

    private def relationshipToConnection(rel: Relationship): Option[Connection] = for {
        userId <- Prop.UserId from rel
        timestamp <- Prop.Timestamp from rel
        arrow = neo.Arrow(rel.getType)
    } yield Connection(userId, timestamp, arrow)

}
