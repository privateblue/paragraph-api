package api.paragraph

import api.base._

import model.base._

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

    def append(timestamp: Long, userId: UserId, blockId: BlockId, target: BlockId, title: Option[String], blockBody: BlockBody) = {
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (b:${Label.Block} {${Prop.BlockId =:= target}})
                          OPTIONAL MATCH (x:${Label.User})-[:${Arrow.Author}]->(b)
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
                val authorId = "x" >>: Prop.UserId from row toOption
                val userName = "a" >>: Prop.UserName from row
                (authorId, validate(userName))
            } else throw NeoException("Append failed")

        Query.result(query)(read)
    }

    def prepend(timestamp: Long, userId: UserId, blockId: BlockId, target: BlockId, title: Option[String], blockBody: BlockBody) = {
        val query = neo"""MATCH (a:${Label.User} {${Prop.UserId =:= userId}}),
                                (b:${Label.Block} {${Prop.BlockId =:= target}})
                          OPTIONAL MATCH (x:${Label.User})-[:${Arrow.Author}]->(b)
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
                val authorId = "x" >>: Prop.UserId from row toOption
                val userName = "a" >>: Prop.UserName from row
                (authorId, validate(userName))
            } else throw NeoException("Prepend failed")

        Query.result(query)(read)
    }

    def link(timestamp: Long, userId: Option[UserId], from: BlockId, to: BlockId) = {
        val query = neo"""MATCH (a:${Label.Block} {${Prop.BlockId =:= from}}),
                                (b:${Label.Block} {${Prop.BlockId =:= to}})
                          OPTIONAL MATCH (x:${Label.User})-[:${Arrow.Author}]->(a)
                          OPTIONAL MATCH (y:${Label.User})-[:${Arrow.Author}]->(b)
                          MERGE (a)-[link:${Arrow.Link}]->(b)
                          ON CREATE SET ${"link" >>: Prop.UserId =:= userId},
                                        ${"link" >>: Prop.Timestamp =:= timestamp}
                          RETURN ${"x" >>: Prop.UserId}, ${"y" >>: Prop.UserId}"""

        def read(result: Result) =
            if (result.getQueryStatistics.containsUpdates && result.hasNext) {
                val row = result.next().toMap
                val fromAuthorId = "x" >>: Prop.UserId from row toOption
                val toAuthorId = "y" >>: Prop.UserId from row toOption

                (fromAuthorId, toAuthorId)
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
