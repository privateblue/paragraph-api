package api.read

import api.base.Actions
import api.base.ApiError

import model.base._
import model.read._

import neo._

import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Relationship
import org.neo4j.graphdb.Direction

import play.api.mvc._

import scalaz._

import scala.collection.JavaConversions._

class ReadController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    import api.base.NeoModel._

    def path(blockIds: Seq[BlockId]) = Actions.public(parse.empty) { (_, _) =>
        val exec = Query.lift { db =>
            blockIds.foldLeft(List.empty[Block]) {
                case (Nil, id) =>
                    val block = for {
                        first <- Option(db.findNode(Label.Block, Prop.BlockId.name, NeoValue(id).underlying))
                        b <- nodeToBlock(first)
                    } yield b
                    block.map(_::Nil).getOrElse(throw ApiError(404, s"Block $id not found"))
                case (path, id) =>
                    if (path.head.outgoing.exists(c => c.blockId == id && c.connection.arrow == Arrow.Link)) {
                        val block = for {
                            next <- Option(db.findNode(Label.Block, Prop.BlockId.name, NeoValue(id).underlying))
                            b <- nodeToBlock(next)
                        } yield b
                        block.map(_::path).getOrElse(throw ApiError(404, s"Block $id not found"))
                    } else throw ApiError(404, s"""Path from ${path.map(_.blockId).reverse.mkString("-->")} to $id not found""")
            }.reverse
        }
        global.neo.run(exec)
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
