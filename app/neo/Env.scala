package neo

import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.GraphDatabaseService

import org.slf4j.Logger

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

case class Env(
    dbPath: String,
    logger: Logger,
    executionContext: ExecutionContext) {

    private val db = new GraphDatabaseFactory().newEmbeddedDatabase(dbPath)

    def run[T, R](nq: Query.Exec[T])(success: T => R, failure: Throwable => R): Future[R] = Future {
        val tx = db.beginTx()
        val result = nq(db).fold(
            l = { e =>
                tx.failure()
                logger.error(e.getMessage)
                failure(e)
            },
            r = { res =>
                tx.success()
                success(res)
            }
        )
        tx.close()
        result
    } (executionContext)

    def shutdown(): Future[Unit] = Future {
        db.shutdown()
    } (executionContext)
}
