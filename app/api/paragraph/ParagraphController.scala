package api.paragraph

import api._
import api.base._

import model.base._

import neo._

import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Result

import org.mindrot.jbcrypt.BCrypt

import play.api.mvc._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._

class ParagraphController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    import api.base.NeoModel._

    def register = Actions.public { (timestamp, body) =>
        val foreignId = (body \ "foreignId").as[String]
        val name = (body \ "name").as[String]
        val password = (body \ "password").as[String]
        val hash = BCrypt.hashpw(password, BCrypt.gensalt)
        val userId = UserId(IdGenerator.key)
        val query = neo"""CREATE (a:${Label.User} {${Prop.UserId =:= userId},
                                                   ${Prop.Timestamp =:= timestamp},
                                                   ${Prop.UserForeignId =:= foreignId},
                                                   ${Prop.UserName =:= name},
                                                   ${Prop.UserPassword =:= hash}})"""

        val prg = Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) userId
            else throw NeoException(s"User $name has not been created")
        }.program

        Program.run(prg, global.env)
    }

    def start = Actions.authenticated { (userId, timestamp, body) =>
        val title = (body \ "title").asOpt[String]
        val blockBody = (body \ "body").as[BlockBody]
        val blockId = BlockId(IdGenerator.key)
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}})
                          MERGE (a)-[:${Arrow.Author} {${Prop.UserId =:= userId},
                                                       ${Prop.Timestamp =:= timestamp}}]->(b:${Label.Block} {${Prop.BlockId =:= blockId},
                                                                                                             ${Prop.Timestamp =:= timestamp},
                                                                                                             ${Prop.BlockTitle =:= title},
                                                                                                             ${Prop.BlockBodyLabel =:= blockBody.label},
                                                                                                             ${Prop.BlockBody =:= blockBody}})"""

        val prg = Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) blockId
            else throw NeoException("Block has not been created")
        }.program

        Program.run(prg, global.env)
    }

    def append = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]
        val title = (body \ "title").asOpt[String]
        val blockBody = (body \ "body").as[BlockBody]
        val blockId = BlockId(IdGenerator.key)
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (b:${Label.Block} {${Prop.BlockId =:= target}})
                          MERGE (b)-[:${Arrow.Link} {${Prop.UserId =:= userId},
                                                     ${Prop.Timestamp =:= timestamp}}]->(c:${Label.Block} {${Prop.BlockId =:= blockId},
                                                                                                           ${Prop.Timestamp =:= timestamp},
                                                                                                           ${Prop.BlockTitle =:= title},
                                                                                                           ${Prop.BlockBodyLabel =:= blockBody.label},
                                                                                                           ${Prop.BlockBody =:= blockBody}})<-[:${Arrow.Author} {${Prop.UserId =:= userId},
                                                                                                                                                                 ${Prop.Timestamp =:= timestamp}}]-(a)"""

        val prg = Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) blockId
            else throw NeoException("Append failed")
        }.program

        Program.run(prg, global.env)
    }

    def prepend = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]
        val title = (body \ "title").asOpt[String]
        val blockBody = (body \ "body").as[BlockBody]
        val blockId = BlockId(IdGenerator.key)
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (b:${Label.Block} {${Prop.BlockId =:= target}})
                          MERGE (b)<-[:${Arrow.Link} {${Prop.UserId =:= userId},
                                                      ${Prop.Timestamp =:= timestamp}}]-(c:${Label.Block} {${Prop.BlockId =:= blockId},
                                                                                                           ${Prop.Timestamp =:= timestamp},
                                                                                                           ${Prop.BlockTitle =:= title},
                                                                                                           ${Prop.BlockBodyLabel =:= blockBody.label},
                                                                                                           ${Prop.BlockBody =:= blockBody}})<-[:${Arrow.Author} {${Prop.UserId =:= userId},
                                                                                                                                                                 ${Prop.Timestamp =:= timestamp}}]-(a)"""

        val prg = Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) blockId
            else throw NeoException("Prepend failed")
        }.program

        Program.run(prg, global.env)
    }

    def link = Actions.authenticated { (userId, timestamp, body) =>
        val from = (body \ "from").as[BlockId]
        val to = (body \ "to").as[BlockId]
        val query = neo"""MATCH (a:${Label.Block} {${Prop.BlockId =:= from}}),
                                (b:${Label.Block} {${Prop.BlockId =:= to}})
                          MERGE (a)-[link:${Arrow.Link}]->(b)
                          ON CREATE SET ${Prop.UserId =:= userId of "link"},
                                        ${Prop.Timestamp =:= timestamp of "link"}"""

        val prg = Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Already linked")
        }.program

        Program.run(prg, global.env)
    }

    def view = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (b:${Label.Block} {${Prop.BlockId =:= target}})
                          WHERE NOT (a)-[:${Arrow.Author}]->(b)
                          MERGE (a)-[view:${Arrow.View}]->(b)
                          ON CREATE SET ${Prop.UserId =:= userId of "view"},
                                        ${Prop.Timestamp =:= timestamp of "view"}"""

        val prg = Query.result(query) { result =>
            ()
        }.program

        Program.run(prg, global.env)
    }

    def follow = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (b:${Label.User} {${Prop.UserId =:= target}})
                          MERGE (a)-[follow:${Arrow.Follow}]->(b)
                          ON CREATE SET ${Prop.UserId =:= userId of "follow"},
                                        ${Prop.Timestamp =:= timestamp of "follow"}"""

        val prg = Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Already followed")
        }.program

        Program.run(prg, global.env)
    }

    def unfollow = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}})-[r:${Arrow.Follow}]->(b:${Label.User} {${Prop.UserId =:= target}})
                          DELETE r"""

        val prg = Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Unfollow has not been successful")
        }.program

        Program.run(prg, global.env)
    }

    def block = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (b:${Label.User} {${Prop.UserId =:= target}})
                          MERGE (a)-[block:${Arrow.Block}]->(b)
                          ON CREATE SET ${Prop.UserId =:= userId of "block"},
                                        ${Prop.Timestamp =:= timestamp of "block"}"""

        val prg = Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Already blocked")
        }.program

        Program.run(prg, global.env)
    }

    def unblock = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}})-[r:${Arrow.Block}]->(b:${Label.User} {${Prop.UserId =:= target}})
                          DELETE r"""

        val prg = Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Unblocking has not been successful")
        }.program

        Program.run(prg, global.env)
    }

}
