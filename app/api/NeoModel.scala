package api

import neo.Label
import neo.Property
import neo.Arrow

object NeoModel {
    object Label {
        val Block = neo.Label("Block")
        val User = neo.Label("User")
    }

    object Prop {
        val Timestamp = Property("timestamp")
        val BlockId = Property("blockId")
        val BlockTitle = Property("title")
        val BlockBody = Property("body")
        val UserId = Property("userId")
        val UserForeignId = Property("foreignId")
        val UserName = Property("name")
    }

    object Arrow {
        val Post = neo.Arrow("POST")
        val Reply = neo.Arrow("REPLY")
        val Share = neo.Arrow("SHARE")
        val Link = neo.Arrow("LINK")
        val BeforeQuote = neo.Arrow("BEFORE_QUOTE")
        val AfterQuote = neo.Arrow("AFTER_QUOTE")
        val Author = neo.Arrow("AUTHOR")
        val Follow = neo.Arrow("FOLLOW")
        val Block = neo.Arrow("BLOCK")
    }
}
