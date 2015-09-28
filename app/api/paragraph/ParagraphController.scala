package api.paragraph

import api._
import api.base.Actions
import api.base.IdGenerator
import api.messaging.Messages

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

        val exec = Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) userId
            else throw NeoException(s"User $name has not been created")
        }

        for {
            result <- global.neo.run(exec)
            messaging = Messages.send("registered", model.paragraph.Registered(userId, timestamp, foreignId, name, password))
            _ <- global.kafka.run(messaging)
        } yield result
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

        val exec = Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) blockId
            else throw NeoException("Block has not been created")
        }

        for {
            result <- global.neo.run(exec)
            messaging = Messages.send("started", model.paragraph.Started(userId, timestamp, title, blockBody))
            _ <- global.kafka.run(messaging)
        } yield result
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

        val exec = Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) blockId
            else throw NeoException("Append failed")
        }

        for {
            result <- global.neo.run(exec)
            messaging = Messages.send("appended", model.paragraph.Appended(userId, timestamp, target, title, blockBody))
            _ <- global.kafka.run(messaging)
        } yield result
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

        val exec = Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) blockId
            else throw NeoException("Prepend failed")
        }

        for {
            result <- global.neo.run(exec)
            messaging = Messages.send("prepended", model.paragraph.Prepended(userId, timestamp, target, title, blockBody))
            _ <- global.kafka.run(messaging)
        } yield result
    }

    def link = Actions.authenticated { (userId, timestamp, body) =>
        val from = (body \ "from").as[BlockId]
        val to = (body \ "to").as[BlockId]
        val query = neo"""MATCH (a:${Label.Block} {${Prop.BlockId =:= from}}),
                                (b:${Label.Block} {${Prop.BlockId =:= to}})
                          MERGE (a)-[link:${Arrow.Link}]->(b)
                          ON CREATE SET ${Prop.UserId =:= userId of "link"},
                                        ${Prop.Timestamp =:= timestamp of "link"}"""

        val exec = Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Already linked")
        }

        for {
            result <- global.neo.run(exec)
            messaging = Messages.send("linked", model.paragraph.Linked(userId, timestamp, from, to))
            _ <- global.kafka.run(messaging)
        } yield result
    }

    def view = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (b:${Label.Block} {${Prop.BlockId =:= target}})
                          WHERE NOT (a)-[:${Arrow.Author}]->(b)
                          MERGE (a)-[view:${Arrow.View}]->(b)
                          ON CREATE SET ${Prop.UserId =:= userId of "view"},
                                        ${Prop.Timestamp =:= timestamp of "view"}"""

        val exec = Query.result(query)(_.getQueryStatistics.containsUpdates)

        for {
            added <- global.neo.run(exec)
            messaging = if (added) Messages.send("viewed", model.paragraph.Viewed(userId, timestamp, target))
                        else Messages.noop
            _ <- global.kafka.run(messaging)
        } yield ()
    }

    def follow = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (b:${Label.User} {${Prop.UserId =:= target}})
                          MERGE (a)-[follow:${Arrow.Follow}]->(b)
                          ON CREATE SET ${Prop.UserId =:= userId of "follow"},
                                        ${Prop.Timestamp =:= timestamp of "follow"}"""

        val exec = Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Already followed")
        }

        for {
            result <- global.neo.run(exec)
            messaging = Messages.send("followed", model.paragraph.Followed(userId, timestamp, target))
            _ <- global.kafka.run(messaging)
        } yield result
    }

    def unfollow = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}})-[r:${Arrow.Follow}]->(b:${Label.User} {${Prop.UserId =:= target}})
                          DELETE r"""

        val exec = Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Unfollow has not been successful")
        }

        for {
            result <- global.neo.run(exec)
            messaging = Messages.send("unfollowed", model.paragraph.Unfollowed(userId, timestamp, target))
            _ <- global.kafka.run(messaging)
        } yield result
    }

    def block = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (b:${Label.User} {${Prop.UserId =:= target}})
                          MERGE (a)-[block:${Arrow.Block}]->(b)
                          ON CREATE SET ${Prop.UserId =:= userId of "block"},
                                        ${Prop.Timestamp =:= timestamp of "block"}"""

        val exec = Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Already blocked")
        }

        for {
            result <- global.neo.run(exec)
            messaging = Messages.send("blocked", model.paragraph.Blocked(userId, timestamp, target))
            _ <- global.kafka.run(messaging)
        } yield result
    }

    def unblock = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}})-[r:${Arrow.Block}]->(b:${Label.User} {${Prop.UserId =:= target}})
                          DELETE r"""

        val exec = Query.result(query) { result =>
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Unblocking has not been successful")
        }

        for {
            result <- global.neo.run(exec)
            messaging = Messages.send("unblocked", model.paragraph.Unblocked(userId, timestamp, target))
            _ <- global.kafka.run(messaging)
        } yield result
    }

}
