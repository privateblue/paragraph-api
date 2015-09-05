package api

import model._

import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Relationship
import org.neo4j.graphdb.Direction

import scalaz._

import scala.collection.JavaConversions._

object NeoModel {
    object Label {
        val Block = neo.Label("Block")
        val User = neo.Label("User")
    }

    object Prop {
        val Timestamp = neo.Property("timestamp")
        val BlockId = neo.Property("blockId")
        val BlockTitle = neo.Property("title")
        val BlockBody = neo.Property("body")
        val BlockBodyType = neo.Property("bodyType")
        val UserId = neo.Property("userId")
        val UserForeignId = neo.Property("foreignId")
        val UserName = neo.Property("name")
        val UserPassword = neo.Property("password")
    }

    object Arrow {
        val Link = neo.Arrow("LINK")
        val Author = neo.Arrow("AUTHOR")
        val View = neo.Arrow("VIEW")
        val Follow = neo.Arrow("FOLLOW")
        val Block = neo.Arrow("BLOCK")
    }

    // TODO validation if all properties are present
    def relationshipToConnection(base: Node, rel: Relationship) = Connection(
        userId = UserId(rel.getProperty(Prop.UserId.name).asInstanceOf[String]),
        timestamp = rel.getProperty(Prop.Timestamp.name).asInstanceOf[Long],
        arrow = neo.Arrow(rel.getType.name),
        blockId = {
            val node = rel.getOtherNode(base)
            BlockId(node.getProperty(Prop.BlockId.name).asInstanceOf[String])
        }
    )

    // TODO validation if node is really a user node with all properties present
    def nodeToUser(node: Node) = User(
        userId = UserId(node.getProperty(Prop.UserId.name).asInstanceOf[String]),
        timestamp = node.getProperty(Prop.Timestamp.name).asInstanceOf[Long],
        foreignId = node.getProperty(Prop.UserForeignId.name).asInstanceOf[String],
        name = node.getProperty(Prop.UserName.name).asInstanceOf[String]
    )

    // TODO validation if node is really a block node with all properties present
    def nodeToBlock(node: Node) = Block(
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
