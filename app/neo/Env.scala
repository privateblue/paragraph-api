package neo

import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.logging.slf4j.Slf4jLogProvider

import org.slf4j.Logger

import scalaz._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

case class Env(
    dbPath: String,
    logger: Logger,
    executionContext: ExecutionContext) {

    private val db =
        new GraphDatabaseFactory()
            .setUserLogProvider(new Slf4jLogProvider)
            .newEmbeddedDatabase(dbPath)

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
