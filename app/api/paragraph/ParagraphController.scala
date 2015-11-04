package api.paragraph

import api._
import api.base._
import api.messaging.Messages
import api.notification.Notifications
import api.external.Pages

import model.base._
import model.external.Page
import model.external.Paragraph

import neo._

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

        val prg = for {
    	    result <- Graph.register(timestamp, userId, name, hash, foreignId).program
    	    _ <- Messages.send("registered", model.paragraph.Registered(userId, timestamp, foreignId, name, password)).program
    	} yield userId

        Program.run(prg, global.env)
    }

    def start = Actions.authenticated { (userId, timestamp, body) =>
        val title = (body \ "title").asOpt[String]
        val blockBody = (body \ "body").as[BlockBody]
        val blockId = BlockId(IdGenerator.key)

        val prg = for {
    	    result <- Graph.start(timestamp, userId, blockId, title, blockBody).program
    	    _ <- Messages.send("started", model.paragraph.Started(blockId, userId, timestamp, title, blockBody)).program
        } yield blockId

        Program.run(prg, global.env)
    }

    def append = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]
        val title = (body \ "title").asOpt[String]
        val blockBody = (body \ "body").as[BlockBody]
        val blockId = BlockId(IdGenerator.key)

        val prg = for {
    	    result <- Graph.append(timestamp, userId, blockId, target, title, blockBody).program
            (authorId, userName) = result
    	    _ <- Messages.send("appended", model.paragraph.Appended(blockId, userId, timestamp, target, title, blockBody)).program
            _ <- authorId match {
                case Some(id) if userId != id => notify(id, timestamp, s"$userName has replied to your block", target, blockId)
                case _ => Program.noop
            }
        } yield blockId

        Program.run(prg, global.env)
    }

    def prepend = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[BlockId]
        val title = (body \ "title").asOpt[String]
        val blockBody = (body \ "body").as[BlockBody]
        val blockId = BlockId(IdGenerator.key)

        val prg = for {
    	    result <- Graph.prepend(timestamp, userId, blockId, target, title, blockBody).program
            (authorId, userName) = result
    	    _ <- Messages.send("prepended", model.paragraph.Prepended(blockId, userId, timestamp, target, title, blockBody)).program
            _ <- authorId match {
                case Some(id) if userId != id => notify(id, timestamp, s"$userName has shared your block", blockId, target)
                case _ => Program.noop
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
            userName <- Query.result(neo"""MATCH (u:${Label.User} {${Prop.UserId =:= userId}}) RETURN ${"u" >>: Prop.UserName}""") { result =>
                if (result.hasNext) {
                    val row = result.next().toMap
                    val userName = "u" >>: Prop.UserName from row
                    validate(userName)
                } else throw NeoException(s"User $userId not found")
            }.program
    	    _ <- Messages.send("linked", model.paragraph.Linked(Some(userId), timestamp, from, to)).program
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
                case Some(userName) => Messages.send("viewed", model.paragraph.Viewed(userId, userName, timestamp, target)).program
                case _ => Messages.noop.program
            }
        } yield ()

        Program.run(prg, global.env)
    }

    def follow = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]

        val prg = for {
    	    result <- Graph.follow(timestamp, userId, target).program
    	    _ <- Messages.send("followed", model.paragraph.Followed(userId, timestamp, target)).program
        } yield result

        Program.run(prg, global.env)
    }

    def unfollow = Actions.authenticated { (userId, timestamp, body) =>
        val target = (body \ "target").as[UserId]

        val prg = for {
    	    result <- Graph.unfollow(timestamp, userId, target).program
    	    _ <- Messages.send("unfollowed", model.paragraph.Unfollowed(userId, timestamp, target)).program
        } yield ()

        Program.run(prg, global.env)
    }

    def download = Actions.authenticated { (_, timestamp, body) =>
        val url = (body \ "url").as[String]
        val pageId = PageId(IdGenerator.key)

        def storyFrom(page: Page): (List[BlockId], Query.Exec[BlockId]) = page.paragraphs match {
            case first::rest =>
                val firstId = BlockId(IdGenerator.key)
                rest.foldLeft((List(firstId), Graph.start(timestamp, pageId, firstId, Some(page.title), Paragraph.blockBody(first)))) {
                    case ((blockIds, previous), paragraph) =>
                        val nextId = BlockId(IdGenerator.key)
                        val continued = previous.flatMap(prevId => Graph.continue(timestamp, nextId, prevId, Paragraph.heading(paragraph), Paragraph.blockBody(paragraph)))
                        (nextId::blockIds, continued)
                }
            case _ => (List(), Query.error(ApiError(500, "Failed to download anything")))
        }

        val prg = for {
            page <- Pages.parse(url).program
            _ <- Graph.register(timestamp, pageId, page.url, page.author, page.title, page.site).program
            // TODO messaging if page newly added
            (blockIds, story) = storyFrom(page) // TODO only if page newly added
            _ <- story.program // TODO only if page newly added
            // TODO messaging if page newly added
        } yield blockIds.reverse

        Program.run(prg, global.env)
    }

    private def notify(userId: UserId, timestamp: Long, text: String, from: BlockId, to: BlockId) = for {
        notification <- Notifications.notifyAboutBlock(userId, timestamp, text, from, to).program
        _ <- Messages.send("notification", notification).program
    } yield ()

}
