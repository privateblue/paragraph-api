package neo

import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Result

import scalaz._
import Scalaz._

import scala.collection.JavaConversions._

object NeoQuery {
    type Err[T] = Throwable \/ T
    type Exec[T] = ReaderT[Err, GraphDatabaseService, T]

    def constant[T](value: Err[T]) =
        Kleisli[Err, GraphDatabaseService, T](_ => value)

    def exec[T](query: String)(fn: Result => Err[T]): Exec[T] =
        exec(query, Map.empty[String, AnyRef])(fn)

    def exec[T](query: String, params: Map[String, AnyRef])(fn: Result => Err[T]): Exec[T] =
        Kleisli[Err, GraphDatabaseService, T] { db =>
            for {
                result <- \/.fromTryCatchNonFatal {
                    db.execute(query, params)
                }.leftMap(e => NeoException(e.getMessage))

                ret <- fn(result)

                _ = result.close()
            } yield ret
        }
}
