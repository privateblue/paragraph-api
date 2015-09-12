package api

import model._

object NeoModel {
    object Label {
        val Block = neo.Label("Block")
        val User = neo.Label("User")
    }

    object Prop {
        val Timestamp = neo.Property[Long]("timestamp")
        val BlockId = neo.Property[BlockId]("blockId")
        val BlockTitle = neo.Property[String]("title")
        val BlockBodyLabel = neo.Property[String]("bodyType")
        val BlockBody = neo.Property[BlockBody]("body")
        val TextBody = neo.Property[Text](BlockBody.name)
        val ImageBody = neo.Property[Image](BlockBody.name)
        val UserId = neo.Property[UserId]("userId")
        val UserForeignId = neo.Property[String]("foreignId")
        val UserName = neo.Property[String]("name")
        val UserPassword = neo.Property[String]("password")
    }

    object Arrow {
        val Link = neo.Arrow("LINK")
        val Author = neo.Arrow("AUTHOR")
        val View = neo.Arrow("VIEW")
        val Follow = neo.Arrow("FOLLOW")
        val Block = neo.Arrow("BLOCK")
    }
}
