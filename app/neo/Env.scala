package neo

import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.logging.slf4j.Slf4jLogProvider

import org.slf4j.Logger

import scalaz._

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext

case class Env(
    dbPath: String,
    logger: Logger,
    executionContext: ExecutionContext) {

    private val db =
        new GraphDatabaseFactory()
            .setUserLogProvider(new Slf4jLogProvider)
            .newEmbeddedDatabase(dbPath)

    def run[T](nq: Query.Exec[T]): Future[T] = {
        val promise = Promise[T]
        Future {
            val tx = db.beginTx()
            nq(db) match {
                case -\/(e) =>
                    tx.failure()
                    tx.close()
                    logger.error(e.getMessage)
                    promise failure e
                case \/-(res) =>
                    tx.success()
                    tx.close()
                    promise success res
            }
        } (executionContext)
        promise.future
    }

    def shutdown(): Future[Unit] = Future {
        db.shutdown()
    } (executionContext)
}
