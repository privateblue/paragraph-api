package api.external

import api.base._

import model.external._

import org.jsoup._
import org.jsoup.nodes.Element

import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal

import akka.stream.Materializer

import scalaz._
import Scalaz._

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext

object Pages {
    def parse(url: String)(implicit ec: ExecutionContext, mat: Materializer) = for {
        html <- load(url)
        doc = Jsoup.parse(html)
        parsed = page(doc)
    } yield validate(parsed)

    private def load(url: String)(implicit ec: ExecutionContext, mat: Materializer) = http.Command.get(url) {
        case HttpResponse(_, _, entity, _) => Unmarshal(entity).to[String]
    }

    private def page(elem: Element): ValidationNel[Throwable, Page] = {
        (canonicalUrlOf(elem) |@| authorOf(elem) |@| titleOf(elem) |@| siteOf(elem)) {
            case (url, author, title, site) => Page(url, author, title, site, paragraphsOf(elem))
        }
    }

    private def canonicalUrlOf(elem: Element) = metaOf(elem, "meta[property=og:url]")

    private def authorOf(elem: Element) = metaOf(elem, "meta[name=author]")

    private def titleOf(elem: Element) = metaOf(elem, "meta[property=og:title]")

    private def siteOf(elem: Element) = metaOf(elem, "meta[property=og:site_name]")

    private def metaOf(elem: Element, selector: String): ValidationNel[Throwable, String] =
        Validation.fromTryCatchNonFatal[String] {
            val n = elem.select(selector).first
            if (n != null && n.attr("content") != "") n.attr("content")
            else throw ApiError(500, s"$selector not found")
        }.toValidationNel

    private def paragraphsOf(elem: Element): List[Paragraph] = {
        val ps = elem.select("article p:not(aside p):not(:has(small))").toList
        ps.foldLeft(List.empty[Paragraph]) { (list, p) =>
            val links = linksOf(p)
            val heading = headingOf(p)
            if (p.hasText) Paragraph.Text(heading, p.text, links) :: list
            else imagesOf(p).map(Paragraph.Image(heading, _, links)) ++ list
        }.reverse
    }

    private val linkPattern = "^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"

    private def linksOf(elem: Element): List[String] =
        elem.select("a").flatMap { n =>
            val href = n.attr("abs:href")
            if (href.matches(linkPattern)) Some(href) else None
        }.toList

    private def headingOf(elem: Element): Option[String] =
        Option(elem.previousElementSibling).flatMap { prev =>
            if (Set("h1", "h2", "h3", "h4", "h5", "h6").contains(prev.tag.getName)) Some(prev.text)
            else None
        }

    private val imagePattern = "([^\\s]+(\\.(?i)(jpg|png|gif|bmp))$)"

    private def imagesOf(elem: Element): List[String] =
        elem.select("img").flatMap { n =>
            val src = n.attr("src")
            if (src.matches(imagePattern)) Some(src) else None
        }.toList
}
