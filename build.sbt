scalaVersion := "2.10.3"

name := "Human-Task-Manager"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  cache,
  "com.typesafe.akka" % "akka-testkit_2.10" % "2.2.0",
  "com.chuusai" % "shapeless" % "2.0.0-M1" cross CrossVersion.full,
  "com.softwaremill.macwire" %% "macros" % "0.5",
  "org.eligosource" %% "eventsourced-core" % "0.6.0",
  "org.eligosource" %% "eventsourced-journal-leveldb" % "0.6.0",
  "org.eligosource" %% "eventsourced-journal-inmem" % "0.6.0",
  "me.lessis" %% "retry-core" % "0.1.0"
)

resolvers += "Typesafe Snapshots" at "http://repo.typesafe.com/typesafe/snapshots/"

resolvers += "Eligosource Releases" at "http://repo.eligotech.com/nexus/content/repositories/eligosource-releases"

play.Project.playScalaSettings

scalariformSettings
