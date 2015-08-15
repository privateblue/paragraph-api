package model

object NeoModel {
    object Labels {
        val Block = neo.createLabel("Block")
        val User = neo.createLabel("User")
    }

    object Keys {
        val Timestamp = "timestamp"
        val BlockId = "blockId"
        val BlockTitle = "title"
        val BlockBody = "body"
        val UserId = "userId"
        val UserForeignId = "foreignId"
        val UserName = "name"
    }

    def TimestampProperty(value: Long) = neo.createProperty(Keys.Timestamp, value)
    def BlockIdProperty(value: BlockId) = neo.createProperty(Keys.BlockId, value)
    def BlockTitleProperty(value: Option[String]) = neo.createOptionalProperty(Keys.BlockTitle, value)
    def BlockBodyProperty(value: BlockBody) = neo.createProperty(Keys.BlockBody, value)
    def UserIdProperty(value: UserId) = neo.createProperty(Keys.UserId, value)
    def UserForeignIdProperty(value: String) = neo.createProperty(Keys.UserForeignId, value)
    def UserNameProperty(value: String) = neo.createProperty(Keys.UserName, value)

    def RelType(at: ArrowType) = at match {
        case ArrowType.Post => neo.createRelType("POST")
        case ArrowType.Reply => neo.createRelType("REPLY")
        case ArrowType.Share => neo.createRelType("SHARE")
        case ArrowType.Link => neo.createRelType("LINK")
        case ArrowType.BeforeQuote => neo.createRelType("BEFORE_QUOTE")
        case ArrowType.AfterQuote => neo.createRelType("AFTER_QUOTE")
        case ArrowType.Author => neo.createRelType("AUTHOR")
        case ArrowType.Follow => neo.createRelType("FOLLOW")
        case ArrowType.Block => neo.createRelType("BLOCK")
    }
}
