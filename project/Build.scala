import sbt.Keys._
import sbt._

object EverstoreBuild extends Build {

  // Settings used by all modules
  lazy val commonSettings = Seq(
    organization := "everstore",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.11.7"
  )

  lazy val root = Project(id = "root",
    base = file(".")) aggregate(scalaAdapter, akkaAdapter, consoleExample, akkaSprayExample)

  lazy val scalaAdapter = Project(id = "scalaAdapter",
    base = file("scala/adapter")) settings (commonSettings: _*)

  lazy val akkaAdapter = Project(id = "akkaAdapter",
    base = file("scala/adapter-akka")) settings (commonSettings: _*) dependsOn scalaAdapter

  //
  // Scala Examples
  //

  lazy val consoleExample = Project(id = "consoleExample",
    base = file("scala/examples/console-examples")) settings (commonSettings: _*) dependsOn scalaAdapter

  lazy val akkaSprayExample = Project(id = "akkaSprayExample",
    base = file("scala/examples/akka-spray-example")) settings (commonSettings: _*) dependsOn(scalaAdapter, akkaAdapter)

}
