package api.graph

import api.base._

import model.base._
import model.external._

import neo._

import org.neo4j.graphdb.Result

import scalaz._
import Scalaz._

import scala.collection.JavaConverters._

object Graph {
    import api.base.NeoModel._

    def init = for {
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.User}) ASSERT n.${Prop.UserId} IS UNIQUE")
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.User}) ASSERT n.${Prop.UserName} IS UNIQUE")
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.User}) ASSERT n.${Prop.UserForeignId} IS UNIQUE")
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.Block}) ASSERT n.${Prop.BlockId} IS UNIQUE")
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.Page}) ASSERT n.${Prop.PageUrl} IS UNIQUE")
    } yield ()

    def register(timestamp: Long, userId: UserId, name: String, hash: String, foreignId: String) = {
        val query = neo"""CREATE (a:${Label.User} ${l(Prop.UserId =:= userId,
                                                      Prop.Timestamp =:= timestamp,
                                                      Prop.UserForeignId =:= foreignId,
                                                      Prop.UserName =:= name,
                                                      Prop.UserPassword =:= hash)})"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates) userId
            else throw NeoException(s"User $name has not been created")

        Query.result(query)(read)
    }

    def include(timestamp: Long, url: String, author: Option[String], title: Option[String], site: Option[String]) = {
        val query = neo"""MERGE (a:${Label.Page} ${l(Prop.PageUrl =:= url)})
                          ON CREATE SET a += ${l(Prop.Timestamp =:= timestamp,
                                                 Prop.PageAuthor =:= author,
                                                 Prop.PageTitle =:= title,
                                                 Prop.PageSite =:= site)}"""
        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates) url
            else NeoException(s"Page $url has not been included")

        Query.result(query)(read)
    }

    def start(timestamp: Long, userId: Option[UserId], blockId: BlockId, blockBody: BlockBody) = {
        val query = neo"""CREATE (b:${Label.Block} ${l(Prop.BlockId =:= blockId,
                                                       Prop.Timestamp =:= timestamp,
                                                       PropertyValue(blockBody),
                                                       Prop.BlockModified =:= timestamp)})
                          WITH b
                          OPTIONAL MATCH (a:${Label.User} ${l(Prop.UserId =?= userId)})
                          MERGE (a)-[:${Arrow.Author} ${l(Prop.Timestamp =:= timestamp)}]->(b)"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates) blockId
            else throw NeoException("Block has not been created")

        Query.result(query)(read)
    }

    def edit(timestamp: Long, blockId: BlockId, blockBody: BlockBody) = {
        val query = neo"""MATCH (b:${Label.Block} ${l(Prop.BlockId =:= blockId)})
                          SET b += ${l(PropertyValue(blockBody),
                                       Prop.BlockModified =:= timestamp)}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates) blockId
            else throw NeoException("Block has not been updated")

        Query.result(query)(read)
    }

    def append(timestamp: Long, userId: Option[UserId], blockId: BlockId, target: BlockId, blockBody: BlockBody) = {
        val query = neo"""MATCH (b:${Label.Block} ${l(Prop.BlockId =:= target)})
                          OPTIONAL MATCH (x:${Label.User})-[:${Arrow.Author}]->(b)
                          OPTIONAL MATCH (a:${Label.User} ${l(Prop.UserId =?= userId)})
                          MERGE (b)-[:${Arrow.Link} ${l(Prop.UserId =:= userId,
                                                        Prop.Timestamp =:= timestamp)}]->(c:${Label.Block} ${l(Prop.BlockId =:= blockId,
                                                                                                               Prop.Timestamp =:= timestamp,
                                                                                                               PropertyValue(blockBody),
                                                                                                               Prop.BlockModified =:= timestamp)})
                          MERGE (a)-[:${Arrow.Author} ${l(Prop.Timestamp =:= timestamp)}]->(c)
                          RETURN ${"x" >>: Prop.UserId}, ${"a" >>: Prop.UserName}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates && result.hasNext) {
                val row = result.next().asScala.toMap
                val authorId = "x" >>: Prop.UserId from row toOption
                val userName = "a" >>: Prop.UserName from row toOption

                (authorId, userName)
            } else throw NeoException("Append failed")

        Query.result(query)(read)
    }

    def prepend(timestamp: Long, userId: Option[UserId], blockId: BlockId, target: BlockId, blockBody: BlockBody) = {
        val query = neo"""MATCH (b:${Label.Block} ${l(Prop.BlockId =:= target)})
                          OPTIONAL MATCH (x:${Label.User})-[:${Arrow.Author}]->(b)
                          OPTIONAL MATCH (a:${Label.User} ${l(Prop.UserId =?= userId)})
                          MERGE (b)<-[:${Arrow.Link} ${l(Prop.UserId =:= userId,
                                                         Prop.Timestamp =:= timestamp)}]-(c:${Label.Block} ${l(Prop.BlockId =:= blockId,
                                                                                                               Prop.Timestamp =:= timestamp,
                                                                                                               PropertyValue(blockBody),
                                                                                                               Prop.BlockModified =:= timestamp)})
                          MERGE (a)-[:${Arrow.Author} ${l(Prop.Timestamp =:= timestamp)}]->(c)
                          RETURN ${"x" >>: Prop.UserId}, ${"a" >>: Prop.UserName}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates && result.hasNext) {
                val row = result.next().asScala.toMap
                val authorId = "x" >>: Prop.UserId from row toOption
                val userName = "a" >>: Prop.UserName from row toOption

                (authorId, userName)
            } else throw NeoException("Prepend failed")

        Query.result(query)(read)
    }

    def link(timestamp: Long, userId: Option[UserId], from: BlockId, to: BlockId) = {
        val query = neo"""MATCH (a:${Label.Block} ${l(Prop.BlockId =:= from)}),
                                (b:${Label.Block} ${l(Prop.BlockId =:= to)})
                          OPTIONAL MATCH (x:${Label.User})-[:${Arrow.Author}]->(a)
                          OPTIONAL MATCH (y:${Label.User})-[:${Arrow.Author}]->(b)
                          MERGE (a)-[link:${Arrow.Link}]->(b)
                          ON CREATE SET link += ${l(Prop.UserId =:= userId,
                                                    Prop.Timestamp =:= timestamp)}
                          RETURN ${"x" >>: Prop.UserId}, ${"y" >>: Prop.UserId}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates && result.hasNext) {
                val row = result.next().asScala.toMap
                val fromAuthorId = "x" >>: Prop.UserId from row toOption
                val toAuthorId = "y" >>: Prop.UserId from row toOption

                (fromAuthorId, toAuthorId)
            } else throw NeoException("Already linked")

        Query.result(query)(read)
    }

    def view(timestamp: Long, userId: UserId, target: BlockId) = {
        val query = neo"""MATCH (a:${Label.User} ${l(Prop.UserId =:= userId)}),
                                (b:${Label.Block} ${l(Prop.BlockId =:= target)})
                          WHERE NOT (a)-[:${Arrow.Author}]->(b)
                          MERGE (a)-[view:${Arrow.View}]->(b)
                          ON CREATE SET view += ${l(Prop.Timestamp =:= timestamp)}
                          RETURN ${"a" >>: Prop.UserName}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates && result.hasNext) {
                val row = result.next().asScala.toMap
                val userName = "a" >>: Prop.UserName from row
                userName.toOption
            } else None

        Query.result(query)(read)
    }

    def source(timestamp: Long, url: String, blockId: BlockId, index: Long) = {
        val query = neo"""MATCH (p:${Label.Page} ${l(Prop.PageUrl =:= url)}),
                                (b:${Label.Block} ${l(Prop.BlockId =:= blockId)})
                          MERGE (p)-[s:${Arrow.Source} ${l(Prop.SourceIndex =:= index)}]->(b)
                          ON CREATE SET s += ${l(Prop.Timestamp =:= timestamp)}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException(s"Page $url is already a source of this block")

        Query.result(query)(read)
    }

    def follow(timestamp: Long, userId: UserId, target: UserId) = {
        val query = neo"""MATCH (a:${Label.User} ${l(Prop.UserId =:= userId)}),
                                (b:${Label.User} ${l(Prop.UserId =:= target)})
                          MERGE (a)-[follow:${Arrow.Follow}]->(b)
                          ON CREATE SET follow += ${l(Prop.Timestamp =:= timestamp)}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Already followed")

        Query.result(query)(read)
    }

    def unfollow(timestamp: Long, userId: UserId, target: UserId) = {
        val query = neo"""MATCH (a:${Label.User} ${l(Prop.UserId =:= userId)})-[r:${Arrow.Follow}]->(b:${Label.User} ${l(Prop.UserId =:= target)})
                          DELETE r"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates) ()
    	    else throw NeoException("Unfollow has not been successful")

        Query.result(query)(read)
    }
}
