package api.notification

import model.base.UserId
import model.base.BlockId
import model.notification._

import api.base.IdGenerator
import api.base.ApiError

import redis._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

object Notifications {
    def notifyAboutBlock(userId: UserId, timestamp: Long, text: String, from: BlockId, to: BlockId)(implicit ec: ExecutionContext): Command.Exec[Notification] =
        Command { redis =>
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

    def noop = Command(_ => Future.successful(()))

    def get(userId: UserId)(implicit ec: ExecutionContext): Command.Exec[Seq[Notification]] =
        Command { redis =>
            for {
                ids <- redis.smembers[String](key(userId))
                notifications <- if (ids.isEmpty) Future.successful(List())
                                 else redis.mget[Notification](ids:_*)
            } yield notifications.flatten.sorted
        }

    def dismiss(notificationId: NotificationId, userId: UserId)(implicit ec: ExecutionContext): Command.Exec[Unit] =
        Command { redis =>
            for {
                notification <- redis.get[Notification](notificationId.toString)
                ownerId = notification match {
                    case Some(PathNotification(_, _, userId, _, _)) => userId
                    case Some(UserNotification(_, _, userId, _, _)) => userId
                    case _ => throw ApiError(404, "Notification not found")
                }
                _ <- if (ownerId == userId) Future.successful(())
                     else Future.failed(ApiError(401, "Permission denied"))
                _ <- redis.srem[NotificationId](key(userId), notificationId)
                _ <- redis.del(notificationId.toString)
            } yield ()
        }

    private def key(userId: UserId): String = s"$userId-notifications"
}
