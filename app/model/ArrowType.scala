package model

sealed trait ArrowType

object ArrowType {
    case object Post extends ArrowType // FromUniqueArrow
    case object Reply extends ArrowType // ToUniqueArrow
    case object Share extends ArrowType // FromUniqueArrow
    case object Link extends ArrowType // BetweenUniqueArrow
    case object BeforeQuote extends ArrowType // FromUniqueArrow
    case object AfterQuote extends ArrowType // ToUniqueArrow
    case object Author extends ArrowType // ToUniqueArrow
    case object Follow extends ArrowType // BetweenUniqueArrow
    case object Block extends ArrowType // BetweenUniqueArrow
}
