package api

import model._

object NeoModel {
    object Label {
        val Block = neo.Label("Block")
        val User = neo.Label("User")
    }

    object Prop {
        val Timestamp = neo.Property("timestamp")
        val BlockId = neo.Property("blockId")
        val BlockTitle = neo.Property("title")
        val BlockBody = neo.Property("body")
        val BlockBodyType = neo.Property("bodyType")
        val UserId = neo.Property("userId")
        val UserForeignId = neo.Property("foreignId")
        val UserName = neo.Property("name")
        val UserPassword = neo.Property("password")
    }

    object Arrow {
        val Link = neo.Arrow("LINK")
        val Author = neo.Arrow("AUTHOR")
        val View = neo.Arrow("VIEW")
        val Follow = neo.Arrow("FOLLOW")
        val Block = neo.Arrow("BLOCK")
    }
}
