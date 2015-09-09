package api

import model._

import neo._

import org.neo4j.graphdb.Node

import play.api.mvc._

import scala.collection.JavaConversions._

class ReadController @javax.inject.Inject() (implicit global: Global) extends Controller {
    import NeoModel._

    def permalink(blockId: BlockId) = Reader.public {
        for {
            node <- Query.result(neo"""MATCH (b:${Label.Block} {${Prop.BlockId + blockId}}) RETURN b""") { result =>
                val b = result.columnAs[Node]("b")
                if (b.hasNext) b.next()
                else throw ApiError(404, "Block not found")
            }

            block = nodeToBlock(node)
        } yield block
    }

    // TODO validation if all properties are present
    private def relationshipToConnection(base: Node, rel: Relationship) = Connection(
        userId = UserId(rel.getProperty(Prop.UserId.name).asInstanceOf[String]),
        timestamp = rel.getProperty(Prop.Timestamp.name).asInstanceOf[Long],
        arrow = neo.Arrow(rel.getType.name),
        blockId = {
            val node = rel.getOtherNode(base)
            BlockId(node.getProperty(Prop.BlockId.name).asInstanceOf[String])
        }
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
                .map(rel => relationshipToConnection(node, rel))
                .toSeq
        },
        outgoing = {
            node.getRelationships(Direction.OUTGOING)
                .filter(_.getOtherNode(node).getLabels.toSeq.contains(Label.Block))
                .map(rel => relationshipToConnection(node, rel))
                .toSeq
        }
    )
}
