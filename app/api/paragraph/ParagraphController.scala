package api.paragraph

import api._
import api.base._
import api.messaging.Messages
import api.notification.Notifications

import model.base._

import neo._

import org.neo4j.graphdb.Result

import org.mindrot.jbcrypt.BCrypt

import play.api.mvc._

import scalaz._
import Scalaz._

import scala.collection.JavaConversions._

class ParagraphController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
    import api.base.NeoModel._

    import global.executionContext
    import global.system
    import global.materializer

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

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates) userId
            else throw NeoException(s"User $name has not been created")

        val prg = for {
    	    result <- Query.result(query)(read).program
    	    _ <- Messages.send("registered", model.paragraph.Registered(userId, timestamp, foreignId, name, password)).program
    	} yield userId

        Program.run(prg, global.env)
    }

    def start = Actions.authenticated { (userId, timestamp, body) =>
        val title = (body \ "title").asOpt[String]
        val blockBody = (body \ "body").as[BlockBody]
        val blockId = BlockId(IdGenerator.key)

        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}})
                          MERGE (a)-[:${Arrow.Author} {${Prop.Timestamp =:= timestamp}}]->(b:${Label.Block} {${Prop.BlockId =:= blockId},
                                                                                                             ${Prop.Timestamp =:= timestamp},
                                                                                                             ${Prop.BlockTitle =:= title},
                                                                                                             ${Prop.BlockBodyLabel =:= blockBody.label},
                                                                                                             ${Prop.BlockBody =:= blockBody}})"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates) blockId
            else throw NeoException("Block has not been created")

        val prg = for {
    	    result <- Query.result(query)(read).program
    	    _ <- Messages.send("started", model.paragraph.Started(blockId, userId, timestamp, title, blockBody)).program
        } yield blockId

        Program.run(prg, global.env)
    }

    def append = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]
        val title = (body \ "title").asOpt[String]
        val blockBody = (body \ "body").as[BlockBody]
        val blockId = BlockId(IdGenerator.key)

        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (x:${Label.User})-[:${Arrow.Author}]->(b:${Label.Block} {${Prop.BlockId =:= target}})
                          MERGE (b)-[:${Arrow.Link} {${Prop.UserId =:= userId},
                                                     ${Prop.Timestamp =:= timestamp}}]->(c:${Label.Block} {${Prop.BlockId =:= blockId},
                                                                                                           ${Prop.Timestamp =:= timestamp},
                                                                                                           ${Prop.BlockTitle =:= title},
                                                                                                           ${Prop.BlockBodyLabel =:= blockBody.label},
                                                                                                           ${Prop.BlockBody =:= blockBody}})<-[:${Arrow.Author} {${Prop.Timestamp =:= timestamp}}]-(a)
                          RETURN ${"x" >>: Prop.UserId}, ${"a" >>: Prop.UserName}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates && result.hasNext) {
                val row = result.next().toMap
                val authorId = "x" >>: Prop.UserId from row
                val userName = "a" >>: Prop.UserName from row
                val getData = (authorId |@| userName) { case (a, u) => (a, u) }
                validate(getData)
            } else throw NeoException("Append failed")

        val prg = for {
    	    result <- Query.result(query)(read).program
            (authorId, userName) = result
    	    _ <- Messages.send("appended", model.paragraph.Appended(blockId, userId, timestamp, target, title, blockBody)).program
            _ <- if (userId != authorId) notify(authorId, timestamp, s"$userName has replied to your block", target, blockId)
                 else Program.noop
        } yield blockId

        Program.run(prg, global.env)
    }

    def prepend = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]
        val title = (body \ "title").asOpt[String]
        val blockBody = (body \ "body").as[BlockBody]
        val blockId = BlockId(IdGenerator.key)

        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (x:${Label.User})-[:${Arrow.Author}]->(b:${Label.Block} {${Prop.BlockId =:= target}})
                          MERGE (b)<-[:${Arrow.Link} {${Prop.UserId =:= userId},
                                                      ${Prop.Timestamp =:= timestamp}}]-(c:${Label.Block} {${Prop.BlockId =:= blockId},
                                                                                                           ${Prop.Timestamp =:= timestamp},
                                                                                                           ${Prop.BlockTitle =:= title},
                                                                                                           ${Prop.BlockBodyLabel =:= blockBody.label},
                                                                                                           ${Prop.BlockBody =:= blockBody}})<-[:${Arrow.Author} {${Prop.Timestamp =:= timestamp}}]-(a)
                          RETURN ${"x" >>: Prop.UserId}, ${"a" >>: Prop.UserName}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates && result.hasNext) {
                val row = result.next().toMap
                val authorId = "x" >>: Prop.UserId from row
                val userName = "a" >>: Prop.UserName from row
                val getData = (authorId |@| userName) { case (a, u) => (a, u) }
                validate(getData)
            } else throw NeoException("Prepend failed")

        val prg = for {
    	    result <- Query.result(query)(read).program
            (authorId, userName) = result
    	    _ <- Messages.send("prepended", model.paragraph.Prepended(blockId, userId, timestamp, target, title, blockBody)).program
            _ <- if (userId != authorId) notify(authorId, timestamp, s"$userName has shared your block", blockId, target)
                 else Program.noop
        } yield blockId

        Program.run(prg, global.env)
    }

    def link = Actions.authenticated { (userId, timestamp, body) =>
        val from = (body \ "from").as[BlockId]
        val to = (body \ "to").as[BlockId]

        val query = neo"""MATCH (x:${Label.User})-[:${Arrow.Author}]->(a:${Label.Block} {${Prop.BlockId =:= from}}),
                                (y:${Label.User})-[:${Arrow.Author}]->(b:${Label.Block} {${Prop.BlockId =:= to}}),
                                (z:${Label.User} {${Prop.UserId =:= userId}})
                          MERGE (a)-[link:${Arrow.Link}]->(b)
                          ON CREATE SET ${"link" >>: Prop.UserId =:= userId},
                                        ${"link" >>: Prop.Timestamp =:= timestamp}
                          RETURN ${"x" >>: Prop.UserId}, ${"y" >>: Prop.UserId}, ${"z" >>: Prop.UserName}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates && result.hasNext) {
                val row = result.next().toMap
                val fromAuthorId = "x" >>: Prop.UserId from row
                val toAuthorId = "y" >>: Prop.UserId from row
                val userName = "z" >>: Prop.UserName from row
                val getData = (fromAuthorId |@| toAuthorId |@| userName) { case (fa, ta, u) => (fa, ta, u) }
                validate(getData)
            } else throw NeoException("Already linked")

        val prg = for {
    	    result <- Query.result(query)(read).program
            (fromAuthorId, toAuthorId, userName) = result
    	    _ <- Messages.send("linked", model.paragraph.Linked(userId, timestamp, from, to)).program
            _ <- if (userId != fromAuthorId) notify(fromAuthorId, timestamp, s"$userName has linked your block", from, to)
                 else Program.noop
            _ <- if (userId != toAuthorId) notify(toAuthorId, timestamp, s"$userName has linked your block", from, to)
                 else Program.noop
        } yield ()

        Program.run(prg, global.env)
    }

    def view = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]

        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (b:${Label.Block} {${Prop.BlockId =:= target}})
                          WHERE NOT (a)-[:${Arrow.Author}]->(b)
                          MERGE (a)-[view:${Arrow.View}]->(b)
                          ON CREATE SET ${"view" >>: Prop.Timestamp =:= timestamp}
                          RETURN ${"a" >>: Prop.UserName}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates && result.hasNext) {
                val row = result.next().toMap
                val userName = "a" >>: Prop.UserName from row
                userName.toOption
            } else None

        val prg = for {
            result <- Query.result(query)(read).program
	        _ <- result match {
                case Some(userName) => Messages.send("viewed", model.paragraph.Viewed(userId, userName, timestamp, target)).program
                case _ => Messages.noop.program
            }
        } yield ()

        Program.run(prg, global.env)
    }

    def follow = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]

        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (b:${Label.User} {${Prop.UserId =:= target}})
                          MERGE (a)-[follow:${Arrow.Follow}]->(b)
                          ON CREATE SET ${"follow" >>: Prop.Timestamp =:= timestamp}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Already followed")

        val prg = for {
    	    result <- Query.result(query)(read).program
    	    _ <- Messages.send("followed", model.paragraph.Followed(userId, timestamp, target)).program
        } yield result

        Program.run(prg, global.env)
    }

    def unfollow = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]

        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}})-[r:${Arrow.Follow}]->(b:${Label.User} {${Prop.UserId =:= target}})
                          DELETE r"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates) ()
    	    else throw NeoException("Unfollow has not been successful")

        val prg = for {
    	    result <- Query.result(query)(read).program
    	    _ <- Messages.send("unfollowed", model.paragraph.Unfollowed(userId, timestamp, target)).program
        } yield ()

        Program.run(prg, global.env)
    }

    private def notify(userId: UserId, timestamp: Long, text: String, from: BlockId, to: BlockId) = for {
        notification <- Notifications.notifyAboutBlock(userId, timestamp, text, from, to).program
        _ <- Messages.send("notification", notification).program
    } yield ()

}
