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
                else throw NeoException("Reply has not been successful")
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
                else throw NeoException("Share has not been successful")
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
            else throw NeoException("Link has not been successful")
        }
    }

    def quote = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]
        val beforeTitle = (body \ "beforeTitle").asOpt[String]
        val beforeBody = (body \ "beforeBody").as[BlockBody]
        val afterTitle = (body \ "afterTitle").asOpt[String]
        val afterBody = (body \ "afterBody").as[BlockBody]

        for {
            beforeId <- Query.newId.map(BlockId.apply)

            afterId <- Query.newId.map(BlockId.apply)

            query = neo"""MATCH (a:${Label.User} {${Prop.UserId + userId}}),
                                (c:${Label.Block} {${Prop.BlockId + target}})
                          MERGE (a)-[:${Arrow.Author} {${Prop.UserId + userId},
                                                       ${Prop.Timestamp + timestamp}}]->(b:${Label.Block} {${Prop.BlockId + beforeId},
                                                                                                           ${Prop.Timestamp + timestamp},
                                                                                                           ${Prop.BlockTitle + beforeTitle},
                                                                                                           ${Prop.BlockBodyType + beforeBody.bodyType},
                                                                                                           ${Prop.BlockBody + beforeBody}})
                                -[:${Arrow.BeforeQuote} {${Prop.UserId + userId},
                                                         ${Prop.Timestamp + timestamp}}]->(c)-[:${Arrow.AfterQuote} {${Prop.UserId + userId},
                                                                                                                     ${Prop.Timestamp + timestamp}}]->
                                (d:${Label.Block} {${Prop.BlockId + afterId},
                                                   ${Prop.Timestamp + timestamp},
                                                   ${Prop.BlockTitle + afterTitle},
                                                   ${Prop.BlockBodyType + afterBody.bodyType},
                                                   ${Prop.BlockBody + afterBody}})<-[:${Arrow.Author} {${Prop.UserId + userId},
                                                                                                       ${Prop.Timestamp + timestamp}}]-(a)"""

            response <- Query.result(query) { result =>
                if (result.getQueryStatistics.containsUpdates) (beforeId, afterId)
                else throw NeoException("Quote has not been successful")
            }
        } yield response
    }

    def follow = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId + userId}}),
                                (b:${Label.User} {${Prop.UserId + target}})
                          MERGE (a)-[:${Arrow.Follow} {${Prop.UserId + userId},
                                                       ${Prop.Timestamp + timestamp}}]->(b)"""

        Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Follow has not been successful")
        }
    }

    def unfollow = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId + userId}})-[r:${Arrow.Follow}]->(b:${Label.User} {${Prop.UserId + target}})
                          DELETE r"""

        Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Unfollow has not been successful")
        }
    }

    def block = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId + userId}}),
                                (b:${Label.User} {${Prop.UserId + target}})
                          MERGE (a)-[:${Arrow.Block} {${Prop.UserId + userId},
                                                      ${Prop.Timestamp + timestamp}}]->(b)"""

        Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Blocking has not been successful")
        }
    }

    def unblock = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId + userId}})-[r:${Arrow.Block}]->(b:${Label.User} {${Prop.UserId + target}})
                          DELETE r"""

        Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Unblocking has not been successful")
        }
    }

}
