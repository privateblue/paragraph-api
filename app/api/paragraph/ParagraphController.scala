package api.paragraph

import api._
import api.base._
import api.messaging.Messages

import model.base._

import neo._

import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Result

import org.mindrot.jbcrypt.BCrypt

import play.api.mvc._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._

import scalaz.std.scalaFuture._

class ParagraphController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    import api.base.NeoModel._

    implicit val kafkaSystem = global.kafkaSystem
    implicit val kafkaMaterializer = global.kafkaMaterializer

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

        val prg = for {
    	    result <- Query.result(query) { result =>
            	if (result.getQueryStatistics.containsUpdates) userId
            	else throw NeoException(s"User $name has not been created")
            }.program
    	    messaging <- Messages.send("registered", model.paragraph.Registered(userId, timestamp, foreignId, name, password)).program
    	} yield result

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

        val prg = for {
    	    result <- Query.result(query) { result =>
            	if (result.getQueryStatistics.containsUpdates) blockId
            	else throw NeoException("Block has not been created")
            }.program
    	    messaging <- Messages.send("started", model.paragraph.Started(blockId, userId, timestamp, title, blockBody)).program
        } yield result

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

        val prg = for {
    	    result <- Query.result(query) { result =>
            	if (result.getQueryStatistics.containsUpdates) blockId
            	else throw NeoException("Append failed")
            }.program
    	    messaging <- Messages.send("appended", model.paragraph.Appended(blockId, userId, timestamp, target, title, blockBody)).program
        } yield result

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

        val prg = for {
    	    result <- Query.result(query) { result =>
            	if (result.getQueryStatistics.containsUpdates) blockId
            	else throw NeoException("Prepend failed")
            }.program
    	    messaging <- Messages.send("prepended", model.paragraph.Prepended(blockId, userId, timestamp, target, title, blockBody)).program
        } yield result

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

        val prg = for {
    	    result <- Query.result(query) { result =>
            	if (result.getQueryStatistics.containsUpdates) ()
            	else throw NeoException("Already linked")
            }.program
    	    messaging <- Messages.send("linked", model.paragraph.Linked(userId, timestamp, from, to)).program
        } yield result

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

        val prg = for {
            added <- Query.result(query)(_.getQueryStatistics.containsUpdates).program
	        messaging <- if (added) Messages.send("viewed", model.paragraph.Viewed(userId, timestamp, target)).program
                         else Messages.noop.program
        } yield ()

        Program.run(prg, global.env)
    }

    def follow = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (b:${Label.User} {${Prop.UserId =:= target}})
                          MERGE (a)-[follow:${Arrow.Follow}]->(b)
                          ON CREATE SET ${Prop.UserId =:= userId of "follow"},
                                        ${Prop.Timestamp =:= timestamp of "follow"}"""

        val prg = for {
    	    result <- Query.result(query) { result =>
            	if (result.getQueryStatistics.containsUpdates) ()
            	else throw NeoException("Already followed")
            }.program
    	    messaging <- Messages.send("followed", model.paragraph.Followed(userId, timestamp, target)).program
        } yield result

        Program.run(prg, global.env)
    }

    def unfollow = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}})-[r:${Arrow.Follow}]->(b:${Label.User} {${Prop.UserId =:= target}})
                          DELETE r"""

        val prg = for {
    	    result <- Query.result(query) { result =>
            	if (result.getQueryStatistics.containsUpdates) ()
            	else throw NeoException("Unfollow has not been successful")
            }.program
    	    messaging <- Messages.send("unfollowed", model.paragraph.Unfollowed(userId, timestamp, target)).program
        } yield result

        Program.run(prg, global.env)
    }

    def block = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (b:${Label.User} {${Prop.UserId =:= target}})
                          MERGE (a)-[block:${Arrow.Block}]->(b)
                          ON CREATE SET ${Prop.UserId =:= userId of "block"},
                                        ${Prop.Timestamp =:= timestamp of "block"}"""

        val prg = for {
    	    result <- Query.result(query) { result =>
            	if (result.getQueryStatistics.containsUpdates) ()
            	else throw NeoException("Already blocked")
            }.program
    	    messaging <- Messages.send("blocked", model.paragraph.Blocked(userId, timestamp, target)).program
        } yield result

        Program.run(prg, global.env)
    }

    def unblock = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}})-[r:${Arrow.Block}]->(b:${Label.User} {${Prop.UserId =:= target}})
                          DELETE r"""

        val prg = for {
    	    result <- Query.result(query) { result =>
            	if (result.getQueryStatistics.containsUpdates) ()
            	else throw NeoException("Unblocking has not been successful")
            }.program
    	    messaging <- Messages.send("unblocked", model.paragraph.Unblocked(userId, timestamp, target)).program
        } yield result

        Program.run(prg, global.env)
    }

}
