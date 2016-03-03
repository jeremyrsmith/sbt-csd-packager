organization := "io.github.jeremyrsmith"
version := "0.1.2"

name := "sbt-csd-packager"
description := "Package Custom Service Descriptors for Cloudera Manager"
scalaVersion := "2.10.6"
sbtPlugin := true
licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))
publishMavenStyle := false

addCompilerPlugin(
  "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
)

resolvers += Resolver.bintrayRepo("jeremyrsmith", "maven")

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % "0.3.0",
  "io.circe" %% "circe-generic" % "0.3.0",
  "io.circe" %% "circe-parser" % "0.3.0",
  "io.github.jeremyrsmith" %% "csd-base" % "0.1.0"
)

bintrayRepository := "sbt-plugins"
bintrayVcsUrl := Some("https://github.com/jeremyrsmith/sbt-csd-packager")