package api.base

import org.neo4j.graphdb.GraphDatabaseService

import redis.RedisClient

import com.softwaremill.react.kafka.ReactiveKafka

case class Env(
    db: GraphDatabaseService,
    redis: RedisClient,
    kafka: ReactiveKafka
)
