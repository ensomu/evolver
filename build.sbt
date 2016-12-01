name := "evolver"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.datastax.cassandra" % "cassandra-driver-core" % "3.1.2",
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
  "org.slf4j" % "slf4j-api" % "1.7.21",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.21",
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "com.github.scopt" %% "scopt" % "3.5.0"
)
    