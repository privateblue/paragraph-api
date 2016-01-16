package api.external

import api.base._

import model.external._

import scalaz._
import Scalaz._

object Images {
    def parse(url: String): Program[Page] =
        Program.lift(Page(url, None, None, None, List(Paragraph.Image(None, url, List()))))
}
