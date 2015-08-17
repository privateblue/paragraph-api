package api

import model._

import neo._

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

        for {
            userId <- Query.newId.map(UserId.apply)

            query = neo"""CREATE (a:${Label.User} {${Prop.UserId + userId},
                                                   ${Prop.Timestamp + timestamp},
                                                   ${Prop.UserForeignId + foreignId},
                                                   ${Prop.UserName + name}})"""

            response <- Query.result(query) { result =>
                if (result.getQueryStatistics.containsUpdates) userId
                else throw NeoException(s"User $name has not been created")
            }
        } yield response
    }

    def start = Actions.authenticated { (userId, timestamp, body) =>
        val title = (body \ "title").asOpt[String]
        val blockBody = (body \ "body").as[BlockBody]

        for {
            blockId <- Query.newId.map(BlockId.apply)

            query = neo"""MATCH (a:${Label.User} {${Prop.UserId + userId}})
                          MERGE (a)-[:${Arrow.Author} {${Prop.UserId + userId},
                                                       ${Prop.Timestamp + timestamp}}]->(b:${Label.Block} {${Prop.BlockId + blockId},
                                                                                                           ${Prop.Timestamp + timestamp},
                                                                                                           ${Prop.BlockTitle + title},
                                                                                                           ${Prop.BlockBodyType + blockBody.bodyType},
                                                                                                           ${Prop.BlockBody + blockBody}})"""

            response <- Query.result(query) { result =>
                if (result.getQueryStatistics.containsUpdates) blockId
                else throw NeoException("Block has not been created")
            }
        } yield response
    }

    def continue = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]
        val title = (body \ "title").asOpt[String]
        val blockBody = (body \ "body").as[BlockBody]

        for {
            blockId <- Query.newId.map(BlockId.apply)

            query = neo"""MATCH (a:${Label.User} {${Prop.UserId + userId}})-[:${Arrow.Author}]->(b:${Label.Block} {${Prop.BlockId + target}})
                          WHERE NOT (b)-[:${Arrow.Post}]->()
                          MERGE (b)-[:${Arrow.Post} {${Prop.UserId + userId},
                                                     ${Prop.Timestamp + timestamp}}]->(c:${Label.Block} {${Prop.BlockId + blockId},
                                                                                                         ${Prop.Timestamp + timestamp},
                                                                                                         ${Prop.BlockTitle + title},
                                                                                                         ${Prop.BlockBodyType + blockBody.bodyType},
                                                                                                         ${Prop.BlockBody + blockBody}})<-[:${Arrow.Author} {${Prop.UserId + userId},
                                                                                                                                                             ${Prop.Timestamp + timestamp}}]-(a)"""

            response <- Query.result(query) { result =>
                if (result.getQueryStatistics.containsUpdates) blockId
                else throw NeoException("Block has not been created")
            }
        } yield response
    }

    def reply = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]
        val title = (body \ "title").asOpt[String]
        val blockBody = (body \ "body").as[BlockBody]

        for {
            blockId <- Query.newId.map(BlockId.apply)

            query = neo"""MATCH (a:${Label.User} {${Prop.UserId + userId}}),
                                (b:${Label.Block} {${Prop.BlockId + target}})
                          MERGE (b)-[:${Arrow.Reply} {${Prop.UserId + userId},
                                                      ${Prop.Timestamp + timestamp}}]->(c:${Label.Block} {${Prop.BlockId + blockId},
                                                                                                          ${Prop.Timestamp + timestamp},
                                                                                                          ${Prop.BlockTitle + title},
                                                                                                          ${Prop.BlockBodyType + blockBody.bodyType},
                                                                                                          ${Prop.BlockBody + blockBody}})<-[:${Arrow.Author} {${Prop.UserId + userId},
                                                                                                                                                              ${Prop.Timestamp + timestamp}}]-(a)"""

            response <- Query.result(query) { result =>
                if (result.getQueryStatistics.containsUpdates) blockId
                else throw NeoException("Block has not been created")
            }
        } yield response
    }

    def share = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]
        val title = (body \ "title").asOpt[String]
        val blockBody = (body \ "body").as[BlockBody]

        for {
            blockId <- Query.newId.map(BlockId.apply)

            query = neo"""MATCH (a:${Label.User} {${Prop.UserId + userId}}),
                                (b:${Label.Block} {${Prop.BlockId + target}})
                          MERGE (b)<-[:${Arrow.Share} {${Prop.UserId + userId},
                                                       ${Prop.Timestamp + timestamp}}]-(c:${Label.Block} {${Prop.BlockId + blockId},
                                                                                                          ${Prop.Timestamp + timestamp},
                                                                                                          ${Prop.BlockTitle + title},
                                                                                                          ${Prop.BlockBodyType + blockBody.bodyType},
                                                                                                          ${Prop.BlockBody + blockBody}})<-[:${Arrow.Author} {${Prop.UserId + userId},
                                                                                                                                                              ${Prop.Timestamp + timestamp}}]-(a)"""

            response <- Query.result(query) { result =>
                if (result.getQueryStatistics.containsUpdates) blockId
                else throw NeoException("Block has not been created")
            }
        } yield response
    }

    def link = Actions.authenticated { (userId, timestamp, body) =>
        val from = (body \ "from").as[BlockId]
        val to = (body \ "to").as[BlockId]
        val query = neo"""MATCH (a:${Label.Block} {${Prop.BlockId + from}}),
                                (b:${Label.Block} {${Prop.BlockId + to}})
                          MERGE (a)-[:${Arrow.Link} {${Prop.UserId + userId},
                                                     ${Prop.Timestamp + timestamp}}]->(b)"""

        Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Block has not been created")
        }
    }

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
