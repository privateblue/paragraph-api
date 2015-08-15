package api

import model._

import neo.NeoQuery
import neo.NeoException

import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Result

import play.api.mvc._
import play.api.libs.json._

import scalaz._
import Scalaz._

class ParagraphController @javax.inject.Inject() (implicit global: Global) extends Controller {
    import NeoModel._

    def register = Actions.public { (timestamp, body) =>
        val foreignId = (body \ "foreignId").as[String]
        val name = (body \ "name").as[String]
        val userId = UserId(IdGenerator.generate)

        val params = Seq(
            TimestampProperty(timestamp),
            UserForeignIdProperty(foreignId),
            UserNameProperty(name),
            UserIdProperty(userId)
        )
        val query = s"CREATE (a:${Labels.User.name} ${params.toPropString})"

        NeoQuery.exec(query, params.toParams) { result =>
            if (result.getQueryStatistics.containsUpdates) userId.right
            else NeoException(s"User $name has not been created").left
        }
    }

    def start = Actions.public { (timestamp, body) =>
        val title = (body \ "title").asOpt[String]
        val blockBody = (body \ "body").as[BlockBody]
        val blockId = BlockId(IdGenerator.generate)
        val userId = UserId("6fd9cde4-c62b-4c4d-b994-46bb13ed465d")

        val userParam = UserIdProperty(userId)
        val timeParam = TimestampProperty(timestamp)
        val blockParams = Seq(
            timeParam,
            BlockBodyProperty(blockBody),
            BlockIdProperty(blockId),
            BlockTitleProperty(title)
        )
        val query = s"MATCH (a:${Labels.User.name} {${userParam.toPropString}}) MERGE (a)-[:${RelType(ArrowType.Author).name} {${userParam.toPropString}, ${timeParam.toPropString}}]->(b:${Labels.Block.name} ${blockParams.toPropString})"

        NeoQuery.exec(query, (blockParams :+ userParam).toParams) { result =>
            if (result.getQueryStatistics.containsUpdates) blockId.right
            else NeoException(s"Block has not been created").left
        }
    }

    // def continue = Action {
    //
    // }
    //
    // def reply = Action {
    //
    // }
    //
    // def share = Action {
    //
    // }
    //
    // def link = Action {
    //
    // }
    //
    // def quote = Action {
    //
    // }
    //
    // def follow = Action {
    //
    // }
    //
    // def unfollow = Action {
    //
    // }
    //
    // def block = Action {
    //
    // }
    //
    // def unblock = Action {
    //
    // }

}
