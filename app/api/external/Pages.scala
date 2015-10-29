package api.external

import model.external._

import org.jsoup._

import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal

import akka.stream.Materializer

import scalaz._
import Scalaz._

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext

object Pages {
    def parse(url: String)(implicit ec: ExecutionContext, mat: Materializer) = for {
        page <- load(url)
    } yield {
        val doc = Jsoup.parse(page)
        val ps = doc.select("article p:not(aside p):not(:has(small))").toList
        val paragraphs = ps.foldLeft(List.empty[Paragraph]) { (list, p) =>
            val links = p.select("a").map(_.attr("href")).toList
            val title = Option(p.previousElementSibling).flatMap { prev =>
                if (Set("h1", "h2", "h3", "h4", "h5", "h6").contains(prev.tag.getName)) Some(prev.text)
                else None
            }
            if (p.hasText) Text(title, p.text, links) :: list
            else p.select("img").map(img => Image(title, img.attr("src"), links)).toList ++ list
        }.reverse
        val url = doc.select("meta[name=og:url]").first.attr("content")
        val author = doc.select("meta[name=author]").first.attr("content")
        val title = doc.select("meta[property=og:title]").first.attr("content")
        val site = doc.select("meta[property=og:site_name]").first.attr("content")
        Page(url, author, title, site, paragraphs)
    }

    private def load(url: String)(implicit ec: ExecutionContext, mat: Materializer) = http.Command.get(url) {
        case HttpResponse(_, _, entity, _) => Unmarshal(entity).to[String]
    }
}
