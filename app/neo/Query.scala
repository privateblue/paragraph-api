package neo

import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Result

import scalaz._
import Scalaz._

case class Query(q: String, params: java.util.Map[String, java.lang.Object])

object Query {
    type Err[T] = Throwable \/ T
    type Exec[T] = ReaderT[Err, GraphDatabaseService, T]

    def execute(query: Query): Exec[Unit] =
        result(query)(_ => ())

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

    def transaction[T](exec: Exec[T]): Exec[T] = for {
        tx <- lift(_.beginTx())
        wrapped <- exec.mapK[Err, T] {
            case -\/(e) =>
                tx.failure()
                tx.close()
                -\/(e)
            case \/-(res) =>
                tx.success()
                tx.close()
                \/-(res)
        }
    } yield wrapped

    def lift[T](fn: GraphDatabaseService => T): Exec[T] =
        Kleisli[Err, GraphDatabaseService, T] { db =>
            \/.fromTryCatchNonFatal(fn(db)).leftMap(e => NeoException(e.getMessage))
        }

    def error[T](t: Throwable): Exec[T] =
        Kleisli[Err, GraphDatabaseService, T](_ => t.left)
}
