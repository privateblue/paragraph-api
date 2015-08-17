package api

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
