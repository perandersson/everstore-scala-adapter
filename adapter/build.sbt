
name := "Everstore Scala Adapter"

organization := "everstore"

version := "1.0"

scalaVersion := "2.11.7"

//
// Resolvers
//

resolvers ++= Seq(
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  Resolver.mavenLocal
)

//
// Everstore Java adapter
//

libraryDependencies += "everstore" % "vanilla" % "1.0-SNAPSHOT"

//
// Serialization Libraries
//
libraryDependencies ++= Seq(
  "com.twitter" % "chill_2.11" % "0.7.0",
  "org.json4s" %% "json4s-jackson" % "3.3.0"
)

//
// Async Await
//

libraryDependencies += "org.scala-lang.modules" %% "scala-async" % "0.9.4"

//
// Test dependencies
//

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.4" % "test"