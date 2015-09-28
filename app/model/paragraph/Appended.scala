package model.paragraph

import model.base._

import play.api.libs.json._

import play.core.websocket.BasicFrameFormatter

case class Appended(
    userId: UserId,
    timestamp: Long,
    target: BlockId,
    title: Option[String],
    body: BlockBody
)

object Appended {
    implicit val appendedFormat = Json.format[Appended]
    // implicit val appendedFrameFormatter = BasicFrameFormatter.textFrame.transform[Appended](
    //     fba = (appended => Json.toJson(appended).toString),
    //     fab = (text => Json.parse(text).as[Appended])
    // )
}
