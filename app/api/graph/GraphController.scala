package api.graph

import api._
import api.base._
import api.messaging.Messages
import api.notification.Notifications

import model.base._

import neo._

import org.mindrot.jbcrypt.BCrypt

import play.api.mvc._

import scalaz._
import Scalaz._

import scala.collection.JavaConverters._

class GraphController @javax.inject.Inject() (implicit global: api.Global) extends Controller {
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

        val prg = for {
    	    result <- Graph.register(timestamp, userId, name, hash, foreignId).program
    	    _ <- Messages.send("registered", model.graph.Registered(userId, timestamp, foreignId, name, password)).program
    	} yield userId

        Program.run(prg, global.env)
    }

    def start = Actions.authenticated { (userId, timestamp, body) =>
        val blockBody = (body \ "body").as[BlockBody]
        val blockId = BlockId(IdGenerator.key)

        val prg = for {
    	    result <- Graph.start(timestamp, Some(userId), blockId, blockBody).program
    	    _ <- Messages.send("started", model.graph.Started(blockId, Some(userId), timestamp, blockBody)).program
        } yield blockId

        Program.run(prg, global.env)
    }

    def append = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]
        val blockBody = (body \ "body").as[BlockBody]
        val blockId = BlockId(IdGenerator.key)

        val prg = for {
    	    result <- Graph.append(timestamp, Some(userId), blockId, target, blockBody).program
            (authorId, userName) = result
    	    _ <- Messages.send("appended", model.graph.Appended(blockId, Some(userId), timestamp, target, blockBody)).program
            _ <- authorId match {
                case Some(id) if userId != id =>
                    val message = userName.map(name => s"$name has replied to your block").getOrElse("Your block has been replied")
                    notify(id, timestamp, message, target, blockId)
                case _ =>
                    Program.noop
            }
        } yield blockId

        Program.run(prg, global.env)
    }

    def prepend = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]
        val blockBody = (body \ "body").as[BlockBody]
        val blockId = BlockId(IdGenerator.key)

        val prg = for {
    	    result <- Graph.prepend(timestamp, Some(userId), blockId, target, blockBody).program
            (authorId, userName) = result
    	    _ <- Messages.send("prepended", model.graph.Prepended(blockId, Some(userId), timestamp, target, blockBody)).program
            _ <- authorId match {
                case Some(id) if userId != id =>
                    val message = userName.map(name => s"$name has shared your block").getOrElse("Your block has been shared")
                    notify(id, timestamp, message, blockId, target)
                case _ =>
                    Program.noop
            }
        } yield blockId

        Program.run(prg, global.env)
    }

    def link = Actions.authenticated { (userId, timestamp, body) =>
        val from = (body \ "from").as[BlockId]
        val to = (body \ "to").as[BlockId]

        val prg = for {
    	    result <- Graph.link(timestamp, Some(userId), from, to).program
            (fromAuthorId, toAuthorId) = result
            userName <- Query.result(neo"""MATCH (u:${Label.User} ${l(Prop.UserId =:= userId)}) RETURN ${"u" >>: Prop.UserName}""") { result =>
                if (result.hasNext) {
                    val row = result.next().asScala.toMap
                    val userName = "u" >>: Prop.UserName from row
                    validate(userName)
                } else throw NeoException(s"User $userId not found")
            }.program
    	    _ <- Messages.send("linked", model.graph.Linked(Some(userId), timestamp, from, to)).program
            _ <- fromAuthorId match {
                case Some(id) if userId != id => notify(id, timestamp, s"$userName has linked your block", from, to)
                case _ => Program.noop
            }
            _ <- toAuthorId match {
                case Some(id) if userId != id => notify(id, timestamp, s"$userName has linked your block", from, to)
                case _ => Program.noop
            }
        } yield ()

        Program.run(prg, global.env)
    }

    def view = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]

        val prg = for {
            result <- Graph.view(timestamp, userId, target).program
	        _ <- result match {
                case Some(userName) => Messages.send("viewed", model.graph.Viewed(userId, userName, timestamp, target)).program
                case _ => Messages.noop.program
            }
        } yield ()

        Program.run(prg, global.env)
    }

    def follow = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]

        val prg = for {
    	    result <- Graph.follow(timestamp, userId, target).program
    	    _ <- Messages.send("followed", model.graph.Followed(userId, timestamp, target)).program
        } yield result

        Program.run(prg, global.env)
    }

    def unfollow = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]

        val prg = for {
    	    result <- Graph.unfollow(timestamp, userId, target).program
    	    _ <- Messages.send("unfollowed", model.graph.Unfollowed(userId, timestamp, target)).program
        } yield ()

        Program.run(prg, global.env)
    }

    private def notify(userId: UserId, timestamp: Long, text: String, from: BlockId, to: BlockId) = for {
        notification <- Notifications.notifyAboutBlock(userId, timestamp, text, from, to).program
        _ <- Messages.send("notification", notification).program
    } yield ()
}
