package api

import model._

import neo._

import play.api.mvc._

class ReadController @javax.inject.Inject() (implicit global: Global) extends Controller {
    import NeoModel._

    def permalink(blockId: BlockId) = Reader.public[String] {
        ???
    }
}
