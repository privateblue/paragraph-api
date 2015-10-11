package api.base

import scalaz._

case class Config(
    neoPath: String,
    redisHost: String,
    redisPort: Int,
    redisPassword: Option[String],
    sessionExpire: Long,
    zkConnect: String,
    kafkaBrokers: String
)

object Config {
    def apply(config: com.typesafe.config.Config): Config = Config (
        neoPath = config.getString("neo.path"),
        redisHost = config.getString("redis.host"),
        redisPort = \/.fromTryCatchNonFatal {
            config.getString("redis.port").toInt
        }.getOrElse(6379),
        redisPassword = \/.fromTryCatchNonFatal {
            config.getString("redis.password")
        }.toOption,
        sessionExpire = \/.fromTryCatchNonFatal {
            config.getString("session-expire").toLong
        }.getOrElse(0L),
        zkConnect = config.getString("kafka.consumer.zookeeper.connect"),
        kafkaBrokers = config.getString("kafka.producer.bootstrap.servers")
    )
}
