package api.base

import org.neo4j.graphdb.GraphDatabaseService

import redis.RedisClient

import org.apache.kafka.clients.producer.KafkaProducer

case class Env(
    db: GraphDatabaseService,
    redis: RedisClient,
    kafkaProducer: KafkaProducer[String, String],
    kafkaConsumerConfig: java.util.Properties)
