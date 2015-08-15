package api

import model._
import NeoModel._

import neo.NeoQuery
import neo.NeoException

import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Result

import play.api.mvc._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._

import scalaz._
import Scalaz._

import scala.concurrent.Future

class ParagraphController @javax.inject.Inject() (global: Global) extends Controller {
    def register = Action.async(parse.json) { request =>
        val timestamp = System.currentTimeMillis
        val foreignId = (request.body \ "foreignId").as[String]
        val name = (request.body \ "name").as[String]
        val userId = UserId(IdGenerator.generate)

        val params = Seq(
            TimestampProperty(timestamp),
            UserForeignIdProperty(foreignId),
            UserNameProperty(name),
            UserIdProperty(userId)
        )
        val query = s"CREATE (n:${Labels.Block.name} ${params.toPropString})"

        val result = NeoQuery.exec(query, params.toParams) { result =>
            if (result.getQueryStatistics.containsUpdates) ().right
            else NeoException(s"User $name has not been created").left
        }

        val json: Future[JsValue] = global.neo.run(result)(
            success = _ => Json.obj("success" -> true),
            failure = e => Json.obj("error" -> e.getMessage)
        )

        json.map(j => Ok(j.toString))
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
