package api.notification

import model.base.UserId
import model.base.BlockId
import model.notification._

import api.base._
import api.messaging.Messages

import akka.actor.ActorSystem
import akka.stream.Materializer

import scalaz.std.scalaFuture._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

object Notifications {
    def notifyAboutBlock(userId: UserId, timestamp: Long, text: String, from: BlockId, to: BlockId)(implicit ec: ExecutionContext): redis.Command.Exec[Notification] =
        redis.Command { redis =>
            val notificationId = NotificationId(IdGenerator.key)
            val notification = PathNotification(
                notificationId = notificationId,
                timestamp = timestamp,
                userId = userId,
                text = text,
                path = List(from, to)
            )
            val save = for {
                stored <- redis.set[Notification](notificationId.toString, notification)
                indexed <- redis.sadd[NotificationId](key(userId), notificationId)
            } yield notification
            save recoverWith {
                case _ => Future.successful(notification)
            }
        }

    def noop = redis.Command(_ => Future.successful(()))

    private def key(userId: UserId): String = s"$userId-notifications"
}
