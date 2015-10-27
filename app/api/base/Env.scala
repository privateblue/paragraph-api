package api.base

import org.neo4j.graphdb.GraphDatabaseService

import redis.RedisClient

import com.softwaremill.react.kafka.ReactiveKafka

import akka.http.scaladsl.HttpExt

case class Env(
    db: GraphDatabaseService,
    redis: RedisClient,
    kafka: ReactiveKafka,
    http: HttpExt
)
