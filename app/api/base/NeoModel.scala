package api.base

import model.base._

object NeoModel {
    object Label {
        val Block = neo.Label("Block")
        val User = neo.Label("User")
        val Page = neo.Label("Page")
    }

    object Prop {
        val Timestamp = neo.Property[Long]("timestamp")
        val BlockId = neo.Property[BlockId]("blockId")
        val BlockTitle = neo.Property[String]("title")
        val BlockBodyLabel = neo.Property[String]("bodyType")
        val BlockBody = neo.Property[model.base.BlockBody]("body")
        val TextBody = neo.Property[model.base.BlockBody.Text](BlockBody.name)
        val ImageBody = neo.Property[model.base.BlockBody.Image](BlockBody.name)
        val UserId = neo.Property[UserId]("userId")
        val UserForeignId = neo.Property[String]("foreignId")
        val UserName = neo.Property[String]("name")
        val UserPassword = neo.Property[String]("password")
        val PageId = neo.Property[PageId]("pageId")
        val PageUrl = neo.Property[String]("url")
        val PageAuthor = neo.Property[String]("author")
        val PageTitle = neo.Property[String]("title")
        val PageSite = neo.Property[String]("site")
    }

    object Arrow {
        val Link = neo.Arrow("LINK")
        val Author = neo.Arrow("AUTHOR")
        val View = neo.Arrow("VIEW")
        val Follow = neo.Arrow("FOLLOW")
        val Source = neo.Arrow("SOURCE")
    }
}
