name := "paragraph"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
    "org.scalaz" %% "scalaz-core" % "7.1.3",
    "org.neo4j" % "neo4j" % "2.3.0-M02",
    "org.neo4j" % "neo4j-slf4j" % "2.3.0-M02",
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "org.slf4j" % "log4j-over-slf4j" % "1.7.12",
    "com.etaty.rediscala" %% "rediscala" % "1.4.0",
    "org.mindrot" % "jbcrypt" % "0.3m",
    "com.fasterxml.uuid" % "java-uuid-generator" % "3.1.3",
    "org.apache.kafka" % "kafka_2.11" % "0.8.2.1" exclude("org.slf4j", "slf4j-log4j12")
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)

routesGenerator := InjectedRoutesGenerator

routesImport ++= Seq(
    "model.base._"
)
