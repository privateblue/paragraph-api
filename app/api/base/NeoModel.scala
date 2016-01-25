package api.base

import model.base._

import org.neo4j.graphdb.PropertyContainer

import scalaz._
import Scalaz._

object NeoModel {
    object Label {
        val Block = neo.Label("Block")
        val User = neo.Label("User")
        val Page = neo.Label("Page")
    }

    object Arrow {
        val Link = neo.Arrow("LINK")
        val Author = neo.Arrow("AUTHOR")
        val View = neo.Arrow("VIEW")
        val Follow = neo.Arrow("FOLLOW")
        val Source = neo.Arrow("SOURCE")
    }

    object Prop {
        val Timestamp = neo.Property[Long]("timestamp")
        val BlockId = neo.Property[BlockId]("blockId")
        val BlockLabel = neo.Property[String]("label")
        val BlockContent = neo.Property[String]("content")
        val UserId = neo.Property[UserId]("userId")
        val UserForeignId = neo.Property[String]("foreignId")
        val UserName = neo.Property[String]("name")
        val UserPassword = neo.Property[String]("password")
        val PageUrl = neo.Property[String]("url")
        val PageAuthor = neo.Property[String]("author")
        val PageTitle = neo.Property[String]("title")
        val PageSite = neo.Property[String]("site")
        val SourceIndex = neo.Property[Long]("index")
    }

    implicit object BlockBodyPropertyConverter extends neo.PropertyConverter[BlockBody] {
        def prop(body: BlockBody) = body match {
            case BlockBody.Text(text) =>
                neo.PropertyValue.Multi(List(Prop.BlockLabel =:= BlockBody.Label.text, Prop.BlockContent =:= text))
            case BlockBody.Title(text) =>
                neo.PropertyValue.Multi(List(Prop.BlockLabel =:= BlockBody.Label.title, Prop.BlockContent =:= text))
            case BlockBody.Image(uri) =>
                neo.PropertyValue.Multi(List(Prop.BlockLabel =:= BlockBody.Label.image, Prop.BlockContent =:= uri))
        }

        def from(container: PropertyContainer): ValidationNel[Throwable, BlockBody] =
            parse(Prop.BlockLabel from container, Prop.BlockContent from container)

        def from(row: Map[String, java.lang.Object]): ValidationNel[Throwable, BlockBody] =
            parse(Prop.BlockLabel from row, Prop.BlockContent from row)

        private def parse(labelRead: ValidationNel[Throwable, String], bodyRead: ValidationNel[Throwable, String]): ValidationNel[Throwable, BlockBody] =
            (labelRead |@| bodyRead) {
                case (BlockBody.Label.text, body) => BlockBody.Text(body)
                case (BlockBody.Label.title, body) => BlockBody.Title(body)
                case (BlockBody.Label.image, body) => BlockBody.Image(body)
            }
    }
}
