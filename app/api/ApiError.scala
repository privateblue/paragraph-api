package api

import scala.util.control.NoStackTrace

case class ApiError(code: Int, msg: String) extends Exception(msg) with NoStackTrace
