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

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

object Articles {
    def parse(url: String)(implicit ec: ExecutionContext, mat: Materializer) = for {
        html <- load(url)
        doc = Jsoup.parse(html)
        parsed = page(doc).fold(
            fail = es => throw ParseError(es.map(_.getMessage)),
            succ = v => v
        )
    } yield parsed

    private def load(url: String)(implicit ec: ExecutionContext, mat: Materializer) = http.Command.get(url) {
        case HttpResponse(_, _, entity, _) => Unmarshal(entity).to[String]
    }

    private def page(elem: Element): ValidationNel[Throwable, Page] = {
        (canonicalUrlOf(elem) |@| authorOf(elem) |@| titleOf(elem) |@| siteOf(elem) |@| paragraphsOf(elem)) {
            case (url, author, title, site, paragraphs) => Page(url, Some(author), Some(title), Some(site), timeOf(elem).toOption, paragraphs)
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
            else throw new ParseError(s"$selector not found")
        }.toValidationNel

    private def timeOf(elem: Element): ValidationNel[Throwable, Long] =
        Validation.fromTryCatchNonFatal[Long] {
            val times = elem.select("article time")
            val datetime = times.asScala.toList match {
                case Nil => throw new ParseError("No time found")
                case head::Nil => head.attr("datetime")
                case head::tail =>
                    val datePublished = times.select("time[itemprop=datePublished]").first
                    if (datePublished != null) datePublished.attr("datetime")
                    else head.attr("datetime")
            }
            val parsed = try {
                java.time.ZonedDateTime.parse(datetime, java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME)
            } catch {
                case e: java.time.format.DateTimeParseException =>
                    java.time.ZonedDateTime.parse(datetime, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZZ"))
            }
            parsed.toInstant.toEpochMilli
        }.toValidationNel

    private def paragraphsOf(elem: Element): ValidationNel[Throwable, NonEmptyList[Paragraph]] =
        Validation.fromTryCatchNonFatal[NonEmptyList[Paragraph]] {
            val ps = elem.select("article p:not(aside p):not(:has(small)), article h1, article h2, article h3, article h4, article h5, article h6").asScala.toList
            ps match {
                case Nil =>
                    throw new ParseError("No blocks can be parsed")
                case _ =>
                    val paragraphs = ps.foldLeft(List.empty[Paragraph]) { (list, node) =>
                        val links = linksOf(node)
                        if (node.tag.getName.startsWith("h") && node.hasText) Paragraph.Heading(node.text) :: list
                        else if (node.tag.getName == "p" && node.hasText) Paragraph.Text(node.text, links) :: list
                        else imagesOf(node).map(Paragraph.Image(_)) ++ list
                    }.reverse
                    NonEmptyList.nel(paragraphs.head, paragraphs.tail)
            }
        }.toValidationNel

    private val linkPattern = "^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"

    private def linksOf(elem: Element): List[String] =
        elem.select("a").asScala.flatMap { n =>
            val href = n.attr("abs:href")
            if (href.matches(linkPattern)) Some(href) else None
        }.toList

    private def imagesOf(elem: Element): List[String] =
        elem.select("img").asScala.flatMap { n =>
            val src = n.attr("src")
            if (src.matches(Pages.imagePattern)) Some(src) else None
        }.toList
}
