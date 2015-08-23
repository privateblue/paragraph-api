import neo.Arrow

import play.api.libs.json._

package object model {
    implicit val arrowWrites = new Writes[Arrow] {
        def writes(arrow: Arrow) = JsString(arrow.name)
    }
}
