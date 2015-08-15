package neo

import scala.util.control.NoStackTrace

case class NeoException(msg: String) extends Exception(msg) with NoStackTrace
