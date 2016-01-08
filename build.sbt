name := "paragraph-api"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
    "org.scalaz" %% "scalaz-core" % "7.1.3",
    "org.neo4j" % "neo4j" % "2.3.1",
    "org.neo4j" % "neo4j-slf4j" % "2.3.1",
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "org.slf4j" % "log4j-over-slf4j" % "1.7.12",
    "com.etaty.rediscala" %% "rediscala" % "1.4.0",
    "org.mindrot" % "jbcrypt" % "0.3m",
    "com.fasterxml.uuid" % "java-uuid-generator" % "3.1.3",
    "com.softwaremill.reactivekafka" %% "reactive-kafka-core" % "0.8.1",
    "com.typesafe.akka" % "akka-stream-experimental_2.11" % "1.0",
    "com.typesafe.akka" % "akka-http-core-experimental_2.11" % "1.0",
    "com.typesafe.akka" % "akka-http-experimental_2.11" % "1.0",
    "org.jsoup" % "jsoup" % "1.8.3"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)

routesGenerator := InjectedRoutesGenerator

routesImport ++= Seq(
    "model.base._"
)
