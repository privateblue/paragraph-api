package api

import model._

import neo._

import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Relationship
import org.neo4j.graphdb.Direction

import play.api.mvc._

import scalaz._

import scala.collection.JavaConversions._

class ReadController @javax.inject.Inject() (implicit global: Global) extends Controller {
    import NeoModel._

    def path(blockIds: Seq[BlockId]) = Reader.public {
        Query.lift { db =>
            blockIds.foldLeft(List.empty[Block]) {
                case (Nil, id) =>
                    val first = Option(db.findNode(Label.Block, Prop.BlockId.name, NeoValue(id).underlying)).getOrElse(throw ApiError(404, s"Block $id not found"))
                    nodeToBlock(first)::Nil
                case (path, id) =>
                    if (path.head.outgoing.exists(c => c.blockId == id && c.connection.arrow == Arrow.Link)) {
                        val res = db.findNode(Label.Block, Prop.BlockId.name, NeoValue(id).underlying)
                        val next = Option(res).getOrElse(throw ApiError(404, s"Block $id not found"))
                        nodeToBlock(next)::path
                    } else throw ApiError(404, s"""Path from ${path.map(_.blockId).reverse.mkString("-->")} to $id not found""")
            }.reverse
        }
    }

    // TODO validation if all properties are present
    private def relationshipToConnection(rel: Relationship) = Connection(
        userId = UserId(rel.getProperty(Prop.UserId.name).asInstanceOf[String]),
        timestamp = rel.getProperty(Prop.Timestamp.name).asInstanceOf[Long],
        arrow = neo.Arrow(rel.getType.name)
    )

    // TODO validation if node is really a user node with all properties present
    private def nodeToUser(node: Node) = User(
        userId = UserId(node.getProperty(Prop.UserId.name).asInstanceOf[String]),
        timestamp = node.getProperty(Prop.Timestamp.name).asInstanceOf[Long],
        foreignId = node.getProperty(Prop.UserForeignId.name).asInstanceOf[String],
        name = node.getProperty(Prop.UserName.name).asInstanceOf[String]
    )

    // TODO validation if node is really a block node with all properties present
    private def nodeToBlock(node: Node) = Block(
        blockId = BlockId(node.getProperty(Prop.BlockId.name).asInstanceOf[String]),
        title = \/.fromTryCatchNonFatal {
            node.getProperty(Prop.BlockTitle.name).asInstanceOf[String]
        }.toOption,
        timestamp = node.getProperty(Prop.Timestamp.name).asInstanceOf[Long],
        body = {
            val bodyType = node.getProperty(Prop.BlockBodyType.name).asInstanceOf[String]
            bodyType match {
                case BlockBody.Type.text =>
                    Text(node.getProperty(Prop.BlockBody.name).asInstanceOf[String])
                case BlockBody.Type.image =>
                    Image(node.getProperty(Prop.BlockBody.name).asInstanceOf[String])
            }
        },
        author = {
            // TODO null check
            val rel = node.getSingleRelationship(Arrow.Author, Direction.INCOMING)
            nodeToUser(rel.getStartNode)
        },
        incoming = {
            node.getRelationships(Direction.INCOMING)
                .filter(_.getOtherNode(node).getLabels.toSeq.contains(Label.Block))
                .map { rel =>
                    val connection = relationshipToConnection(rel)
                    val other = rel.getStartNode
                    val otherId = BlockId(other.getProperty(Prop.BlockId.name).asInstanceOf[String])
                    BlockConnection(connection, otherId)
                }
                .toSeq
        },
        outgoing = {
            node.getRelationships(Direction.OUTGOING)
                .filter(_.getOtherNode(node).getLabels.toSeq.contains(Label.Block))
                .map { rel =>
                    val connection = relationshipToConnection(rel)
                    val other = rel.getEndNode
                    val otherId = BlockId(other.getProperty(Prop.BlockId.name).asInstanceOf[String])
                    BlockConnection(connection, otherId)
                }
                .toSeq
        },
        seen = {
            node.getRelationships(Direction.INCOMING, Arrow.View)
                .map(relationshipToConnection)
                .toSeq
        }
    )
}
