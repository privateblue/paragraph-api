package api

case class Config(
    neoPath: String
)

object Config {
    def apply(config: com.typesafe.config.Config): Config = Config (
        neoPath = config.getString("neo.path")
    )
}
