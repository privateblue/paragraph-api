package model.paragraph

import model.base._

import play.api.libs.json._

import play.core.websocket.BasicFrameFormatter

case class Viewed(
    userId: UserId,
    timestamp: Long,
    target: BlockId
)

object Viewed {
    implicit val viewedFormat = Json.format[Viewed]
    implicit val viewedFrameFormatter = BasicFrameFormatter.textFrame.transform[Viewed](
        fba = (viewed => Json.toJson(viewed).toString),
        fab = (text => Json.parse(text).as[Viewed])
    )
}
