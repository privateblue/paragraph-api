import scalaz._

import play.api.libs.json._

package object api {
    implicit val unitWrites = new Writes[Unit] {
        def writes(u: Unit) = JsString("success")
    }
    implicit def tuple2Writes[T: Writes, U: Writes] = new Writes[(T, U)] {
        def writes(t: (T, U)) = Json.arr(t._1, t._2)
    }
    implicit def nelWrites[T: Writes] = new Writes[NonEmptyList[T]] {
        def writes(nel: NonEmptyList[T]) = Json.toJson(nel.list)
    }
}
