package api.graph

import api.base._

import model.base._
import model.external._

import neo._

import org.neo4j.graphdb.Result

import scalaz._
import Scalaz._

import scala.collection.JavaConversions._

object Graph {
    import api.base.NeoModel._

    def init = for {
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.User}) ASSERT n.${Prop.UserId} IS UNIQUE")
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.User}) ASSERT n.${Prop.UserName} IS UNIQUE")
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.User}) ASSERT n.${Prop.UserForeignId} IS UNIQUE")
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.Block}) ASSERT n.${Prop.BlockId} IS UNIQUE")
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.Page}) ASSERT n.${Prop.PageId} IS UNIQUE")
        _ <- Query.execute(neo"CREATE CONSTRAINT ON (n:${Label.Page}) ASSERT n.${Prop.PageUrl} IS UNIQUE")
    } yield ()

    def register(timestamp: Long, userId: UserId, name: String, hash: String, foreignId: String) = {
        val query = neo"""CREATE (a:${Label.User} {${Prop.UserId =:= userId},
                                                   ${Prop.Timestamp =:= timestamp},
                                                   ${Prop.UserForeignId =:= foreignId},
                                                   ${Prop.UserName =:= name},
                                                   ${Prop.UserPassword =:= hash}})"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates) userId
            else throw NeoException(s"User $name has not been created")

        Query.result(query)(read)
    }

    def download(timestamp: Long, pageId: PageId, url: String, author: Option[String], title: Option[String], site: Option[String]) = {
        val query = neo"""MERGE (a:${Label.Page} {${Prop.PageUrl =:= url}})
                          ON CREATE SET ${"a" >>: Prop.PageId =:= pageId},
                                        ${"a" >>: Prop.Timestamp =:= timestamp},
                                        ${"a" >>: Prop.PageAuthor =:= author},
                                        ${"a" >>: Prop.PageTitle =:= title},
                                        ${"a" >>: Prop.PageSite =:= site}
                          RETURN ${"a" >>: Prop.PageId}"""
        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates && result.hasNext) {
                val row = result.next().toMap
                "a" >>: Prop.PageId from row toOption
            } else None

        Query.result(query)(read)
    }

    def start(timestamp: Long, userId: UserId, blockId: BlockId, title: Option[String], blockBody: BlockBody) = {
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}})
                          MERGE (a)-[:${Arrow.Author} {${Prop.Timestamp =:= timestamp}}]->(b:${Label.Block} {${Prop.BlockId =:= blockId},
                                                                                                             ${Prop.Timestamp =:= timestamp},
                                                                                                             ${Prop.BlockTitle =:= title},
                                                                                                             ${Prop.BlockBodyLabel =:= blockBody.label},
                                                                                                             ${Prop.BlockBody =:= blockBody}})"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates) blockId
            else throw NeoException("Block has not been created")

        Query.result(query)(read)
    }

    def attach(timestamp: Long, userId: UserId, pageId: PageId, blockId: BlockId, title: Option[String], blockBody: BlockBody) = {
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (p:${Label.Page} {${Prop.PageId =:= pageId}})
                          MERGE (p)-[:${Arrow.Source} {${Prop.Timestamp =:= timestamp},
                                                       ${Prop.SourceIndex =:= 0L}}]->(b:${Label.Block} {${Prop.BlockId =:= blockId},
                                                                                                        ${Prop.Timestamp =:= timestamp},
                                                                                                        ${Prop.BlockTitle =:= title},
                                                                                                        ${Prop.BlockBodyLabel =:= blockBody.label},
                                                                                                        ${Prop.BlockBody =:= blockBody}})<-[:${Arrow.Author} {${Prop.Timestamp =:= timestamp}}]-(a)"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates) blockId
            else throw NeoException("Block has not been created")

        Query.result(query)(read)
    }

    def append(timestamp: Long, userId: UserId, blockId: BlockId, target: BlockId, title: Option[String], blockBody: BlockBody) = {
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (x:${Label.User})-[:${Arrow.Author}]->(b:${Label.Block} {${Prop.BlockId =:= target}})
                          MERGE (b)-[:${Arrow.Link} {${Prop.UserId =:= userId},
                                                     ${Prop.Timestamp =:= timestamp}}]->(c:${Label.Block} {${Prop.BlockId =:= blockId},
                                                                                                           ${Prop.Timestamp =:= timestamp},
                                                                                                           ${Prop.BlockTitle =:= title},
                                                                                                           ${Prop.BlockBodyLabel =:= blockBody.label},
                                                                                                           ${Prop.BlockBody =:= blockBody}})<-[:${Arrow.Author} {${Prop.Timestamp =:= timestamp}}]-(a)
                          RETURN ${"x" >>: Prop.UserId}, ${"a" >>: Prop.UserName}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates && result.hasNext) {
                val row = result.next().toMap
                val authorId = "x" >>: Prop.UserId from row
                val userName = "a" >>: Prop.UserName from row
                (validate(authorId), validate(userName))
            } else throw NeoException("Append failed")

        Query.result(query)(read)
    }

    def continue(timestamp: Long, userId: UserId, pageId: PageId, blockId: BlockId, target: BlockId, title: Option[String], blockBody: BlockBody) = {
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (p:${Label.Page} {${Prop.PageId =:= pageId}})-[s:${Arrow.Source}]->(b:${Label.Block} {${Prop.BlockId =:= target}})<-[:${Arrow.Author}]-(x:${Label.User})
                          MERGE (b)-[:${Arrow.Link} {${Prop.Timestamp =:= timestamp}}]->(c:${Label.Block} {${Prop.BlockId =:= blockId},
                                                                                                           ${Prop.Timestamp =:= timestamp},
                                                                                                           ${Prop.BlockTitle =:= title},
                                                                                                           ${Prop.BlockBodyLabel =:= blockBody.label},
                                                                                                           ${Prop.BlockBody =:= blockBody}})<-[:${Arrow.Source} {${Prop.Timestamp =:= timestamp},
                                                                                                                                                                 ${Prop.SourceIndex}:${"s" >>: Prop.SourceIndex}+1}]-(p)
                          MERGE (c)<-[:${Arrow.Author} {${Prop.Timestamp =:= timestamp}}]-(a)
                          RETURN ${"x" >>: Prop.UserId}, ${"a" >>: Prop.UserName}""" // TODO find way to encode expressions like +1 with neo dsl

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates && result.hasNext) {
                val row = result.next().toMap
                val authorId = "x" >>: Prop.UserId from row
                val userName = "a" >>: Prop.UserName from row
                (validate(authorId), validate(userName))
            } else throw NeoException("Continue failed")

        Query.result(query)(read)
    }

    def prepend(timestamp: Long, userId: UserId, blockId: BlockId, target: BlockId, title: Option[String], blockBody: BlockBody) = {
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (x:${Label.User})-[:${Arrow.Author}]->(b:${Label.Block} {${Prop.BlockId =:= target}})
                          MERGE (b)<-[:${Arrow.Link} {${Prop.UserId =:= userId},
                                                      ${Prop.Timestamp =:= timestamp}}]-(c:${Label.Block} {${Prop.BlockId =:= blockId},
                                                                                                           ${Prop.Timestamp =:= timestamp},
                                                                                                           ${Prop.BlockTitle =:= title},
                                                                                                           ${Prop.BlockBodyLabel =:= blockBody.label},
                                                                                                           ${Prop.BlockBody =:= blockBody}})<-[:${Arrow.Author} {${Prop.Timestamp =:= timestamp}}]-(a)
                          RETURN ${"x" >>: Prop.UserId}, ${"a" >>: Prop.UserName}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates && result.hasNext) {
                val row = result.next().toMap
                val authorId = "x" >>: Prop.UserId from row
                val userName = "a" >>: Prop.UserName from row
                (validate(authorId), validate(userName))
            } else throw NeoException("Prepend failed")

        Query.result(query)(read)
    }

    def link(timestamp: Long, userId: Option[UserId], from: BlockId, to: BlockId) = {
        val query = neo"""MATCH (x:${Label.User})-[:${Arrow.Author}]->(a:${Label.Block} {${Prop.BlockId =:= from}}),
                                (y:${Label.User})-[:${Arrow.Author}]->(b:${Label.Block} {${Prop.BlockId =:= to}})
                          MERGE (a)-[link:${Arrow.Link}]->(b)
                          ON CREATE SET ${"link" >>: Prop.UserId =:= userId},
                                        ${"link" >>: Prop.Timestamp =:= timestamp}
                          RETURN ${"x" >>: Prop.UserId}, ${"y" >>: Prop.UserId}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates && result.hasNext) {
                val row = result.next().toMap
                val fromAuthorId = "x" >>: Prop.UserId from row
                val toAuthorId = "y" >>: Prop.UserId from row

                (validate(fromAuthorId), validate(toAuthorId))
            } else throw NeoException("Already linked")

        Query.result(query)(read)
    }

    def view(timestamp: Long, userId: UserId, target: BlockId) = {
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (b:${Label.Block} {${Prop.BlockId =:= target}})
                          WHERE NOT (a)-[:${Arrow.Author}]->(b)
                          MERGE (a)-[view:${Arrow.View}]->(b)
                          ON CREATE SET ${"view" >>: Prop.Timestamp =:= timestamp}
                          RETURN ${"a" >>: Prop.UserName}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates && result.hasNext) {
                val row = result.next().toMap
                val userName = "a" >>: Prop.UserName from row
                userName.toOption
            } else None

        Query.result(query)(read)
    }

    def follow(timestamp: Long, userId: UserId, target: UserId) = {
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (b:${Label.User} {${Prop.UserId =:= target}})
                          MERGE (a)-[follow:${Arrow.Follow}]->(b)
                          ON CREATE SET ${"follow" >>: Prop.Timestamp =:= timestamp}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates) ()
            else throw NeoException("Already followed")

        Query.result(query)(read)
    }

    def unfollow(timestamp: Long, userId: UserId, target: UserId) = {
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}})-[r:${Arrow.Follow}]->(b:${Label.User} {${Prop.UserId =:= target}})
                          DELETE r"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates) ()
    	    else throw NeoException("Unfollow has not been successful")

        Query.result(query)(read)
    }
}
