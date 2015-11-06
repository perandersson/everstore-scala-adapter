name := "Everstore Console Example"

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

// Async Await
libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-async" % "0.9.4"
)
