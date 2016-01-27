package api.external

import scalaz._
import Scalaz._

import scala.util.control.NoStackTrace

case class ParseError(messages: NonEmptyList[String]) extends Exception(messages.list.mkString("\n")) with NoStackTrace {
    def this(msg: String) = this(NonEmptyList(msg))
}
