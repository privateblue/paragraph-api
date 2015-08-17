package neo

import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Result

import scalaz._
import Scalaz._

import scala.collection.JavaConversions._

case class Query(q: String, params: Map[String, AnyRef])

object Query {
    type Err[T] = Throwable \/ T
    type Exec[T] = ReaderT[Err, GraphDatabaseService, T]

    def newId = java.util.UUID.randomUUID.toString.point[Exec]

    def error[T](e: Throwable): Exec[T] =
        Kleisli[Err, GraphDatabaseService, T](_ => e.left)

    def execute(query: Query): Exec[Unit] =
        result(query)(_ => ().right)

    def result[T](query: Query)(fn: Result => T): Exec[T] =
        Kleisli[Err, GraphDatabaseService, T] { db =>
            for {
                result <- \/.fromTryCatchNonFatal {
                    db.execute(query.q, query.params)
                }.leftMap(e => NeoException(e.getMessage))

                ret <- \/.fromTryCatchNonFatal {
                    fn(result)
                }

                _ = result.close()
            } yield ret
        }
}
