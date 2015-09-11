package api

import model._

import neo._

import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Result

import org.mindrot.jbcrypt.BCrypt

import play.api.mvc._
import play.api.libs.json._

class ParagraphController @javax.inject.Inject() (implicit global: Global) extends Controller {
    import NeoModel._

    def register = Paragraph.public { (timestamp, body) =>
        val foreignId = (body \ "foreignId").as[String]
        val name = (body \ "name").as[String]
        val password = (body \ "password").as[String]
        val hash = BCrypt.hashpw(password, BCrypt.gensalt)
        val userId = UserId(IdGenerator.key)
        val query = neo"""CREATE (a:${Label.User} {${Prop.UserId + userId},
                                                   ${Prop.Timestamp + timestamp},
                                                   ${Prop.UserForeignId + foreignId},
                                                   ${Prop.UserName + name},
                                                   ${Prop.UserPassword + hash}})"""

        Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) userId
            else throw NeoException(s"User $name has not been created")
        }
    }

    def start = Paragraph.authenticated { (userId, timestamp, body) =>
        val title = (body \ "title").asOpt[String]
        val blockBody = (body \ "body").as[BlockBody]
        val blockId = BlockId(IdGenerator.key)
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId + userId}})
                          MERGE (a)-[:${Arrow.Author} {${Prop.UserId + userId},
                                                       ${Prop.Timestamp + timestamp}}]->(b:${Label.Block} {${Prop.BlockId + blockId},
                                                                                                           ${Prop.Timestamp + timestamp},
                                                                                                           ${Prop.BlockTitle + title},
                                                                                                           ${Prop.BlockBodyType + blockBody.bodyType},
                                                                                                           ${Prop.BlockBody + blockBody}})"""

        Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) blockId
            else throw NeoException("Block has not been created")
        }
    }

    def append = Paragraph.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]
        val title = (body \ "title").asOpt[String]
        val blockBody = (body \ "body").as[BlockBody]
        val blockId = BlockId(IdGenerator.key)
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId + userId}}),
                                (b:${Label.Block} {${Prop.BlockId + target}})
                          MERGE (b)-[:${Arrow.Link} {${Prop.UserId + userId},
                                                     ${Prop.Timestamp + timestamp}}]->(c:${Label.Block} {${Prop.BlockId + blockId},
                                                                                                         ${Prop.Timestamp + timestamp},
                                                                                                         ${Prop.BlockTitle + title},
                                                                                                         ${Prop.BlockBodyType + blockBody.bodyType},
                                                                                                         ${Prop.BlockBody + blockBody}})<-[:${Arrow.Author} {${Prop.UserId + userId},
                                                                                                                                                             ${Prop.Timestamp + timestamp}}]-(a)"""

        Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) blockId
            else throw NeoException("Append failed")
        }
    }

    def prepend = Paragraph.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]
        val title = (body \ "title").asOpt[String]
        val blockBody = (body \ "body").as[BlockBody]
        val blockId = BlockId(IdGenerator.key)
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId + userId}}),
                                (b:${Label.Block} {${Prop.BlockId + target}})
                          MERGE (b)<-[:${Arrow.Link} {${Prop.UserId + userId},
                                                      ${Prop.Timestamp + timestamp}}]-(c:${Label.Block} {${Prop.BlockId + blockId},
                                                                                                         ${Prop.Timestamp + timestamp},
                                                                                                         ${Prop.BlockTitle + title},
                                                                                                         ${Prop.BlockBodyType + blockBody.bodyType},
                                                                                                         ${Prop.BlockBody + blockBody}})<-[:${Arrow.Author} {${Prop.UserId + userId},
                                                                                                                                                             ${Prop.Timestamp + timestamp}}]-(a)"""

        Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) blockId
            else throw NeoException("Prepend failed")
        }
    }

    def link = Paragraph.authenticated { (userId, timestamp, body) =>
        val from = (body \ "from").as[BlockId]
        val to = (body \ "to").as[BlockId]
        val query = neo"""MATCH (a:${Label.Block} {${Prop.BlockId + from}}),
                                (b:${Label.Block} {${Prop.BlockId + to}})
                          MERGE (a)-[link:${Arrow.Link}]->(b)
                          ON CREATE SET ${Prop.UserId + userId of "link"},
                                        ${Prop.Timestamp + timestamp of "link"}"""

        Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Already linked")
        }
    }

    def view = Paragraph.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId + userId}}),
                                (b:${Label.Block} {${Prop.BlockId + target}})
                          WHERE NOT (a)-[:${Arrow.Author}]->(b)
                          MERGE (a)-[view:${Arrow.View}]->(b)
                          ON CREATE SET ${Prop.UserId + userId of "view"},
                                        ${Prop.Timestamp + timestamp of "view"}"""

        Query.result(query) { result =>
            ()
        }
    }

    def follow = Paragraph.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId + userId}}),
                                (b:${Label.User} {${Prop.UserId + target}})
                          MERGE (a)-[follow:${Arrow.Follow}]->(b)
                          ON CREATE SET ${Prop.UserId + userId of "follow"},
                                        ${Prop.Timestamp + timestamp of "follow"}"""

        Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Already followed")
        }
    }

    def unfollow = Paragraph.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId + userId}})-[r:${Arrow.Follow}]->(b:${Label.User} {${Prop.UserId + target}})
                          DELETE r"""

        Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Unfollow has not been successful")
        }
    }

    def block = Paragraph.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId + userId}}),
                                (b:${Label.User} {${Prop.UserId + target}})
                          MERGE (a)-[block:${Arrow.Block}]->(b)
                          ON CREATE SET ${Prop.UserId + userId of "block"},
                                        ${Prop.Timestamp + timestamp of "block"}"""

        Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Already blocked")
        }
    }

    def unblock = Paragraph.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId + userId}})-[r:${Arrow.Block}]->(b:${Label.User} {${Prop.UserId + target}})
                          DELETE r"""

        Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Unblocking has not been successful")
        }
    }

}
