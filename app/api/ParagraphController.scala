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
        val query = s"CREATE (n:${Labels.Block.name} ${params.toPropString})"

        NeoQuery.exec(query, params.toParams) { result =>
            if (result.getQueryStatistics.containsUpdates) ().right
            else NeoException(s"User $name has not been created").left
        }
    }

    // def start = Action {
    //
    // }
    //
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
