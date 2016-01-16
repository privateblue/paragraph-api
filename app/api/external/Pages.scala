package api.external

import api.base._

import model.external._

import akka.stream.Materializer

import scala.concurrent.ExecutionContext

object Pages {
    val imagePattern = "([^\\s]+(\\.(?i)(jpg|png|gif|bmp))$)"

    def parse(url: String)(implicit ec: ExecutionContext, mat: Materializer): Program[Page] = url match {

        // IMAGES
        case imagePattern.r(_*) => Images.parse(url)

        // ARTICLES
        case _ => Articles.parse(url).program

    }
}
