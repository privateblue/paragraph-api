package api

import scala.util.control.NoStackTrace

case class ApiException(code: Int, msg: String) extends Exception(msg) with NoStackTrace
