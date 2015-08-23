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
                else throw ApiError(500, "Block not found")
            }

            block = nodeToBlock(node)
        } yield block
    }
}
