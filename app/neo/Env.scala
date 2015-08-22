package neo

import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.GraphDatabaseService

import org.slf4j.Logger

import scalaz._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

case class Env(
    dbPath: String,
    logger: Logger,
    executionContext: ExecutionContext) {

    private val db = new GraphDatabaseFactory().newEmbeddedDatabase(dbPath)

    def run[T](nq: Query.Exec[T]): EitherT[Future, Throwable, T] = EitherT {
        Future {
            val tx = db.beginTx()
            val result = nq(db).bimap(
                { e =>
                    tx.failure()
                    logger.error(e.getMessage)
                    e
                },
                { res =>
                    tx.success()
                    res
                }
            )
            tx.close()
            result
        } (executionContext)
    }

    def shutdown(): Future[Unit] = Future {
        db.shutdown()
    } (executionContext)
}
