name := "Everstore Akka Adapter"

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

val sprayVersion = "1.3.3"
val akkaVersion = "2.3.11"

// Spray

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "io.spray" %% "spray-can" % sprayVersion,
  "io.spray" %% "spray-routing" % sprayVersion,
  "io.spray" %% "spray-json" % "1.3.2",
  "org.json4s" %% "json4s-native" % "3.2.10"
)
