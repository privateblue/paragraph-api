package api.base

import org.neo4j.graphdb.GraphDatabaseService

import redis.RedisClient

case class Env(db: GraphDatabaseService, redis: RedisClient)
