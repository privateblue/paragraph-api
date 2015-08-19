name := "paragraph"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
    "org.scalaz" %% "scalaz-core" % "7.1.3",
    "org.neo4j" % "neo4j" % "2.3.0-M02",
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "com.etaty.rediscala" %% "rediscala" % "1.4.0"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)

routesGenerator := InjectedRoutesGenerator
