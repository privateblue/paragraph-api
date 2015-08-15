import play.api.libs.json._

package object api {
    implicit val unitWrites = new Writes[Unit] {
        def writes(u: Unit) = JsString("success")
    }
    implicit def tupleWrites[T: Writes] = new Writes[(T, T)] {
        def writes(t: (T, T)) = Json.arr(t._1, t._2)
    }
}
