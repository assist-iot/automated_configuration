Global / onChangedBuildSource := ReloadOnSourceChanges

import sbt.Keys.libraryDependencies

name         := "automated-config"
organization := "eu.assist-iot"

val Version = new {
  val akka                     = "2.6.21"
  val scala3                   = "3.1.0"
  val scalaParallelCollections = "1.0.4"
  val scalaTest                = "3.2.17"
  val cats                     = "2.7.0"
  val circe                    = "0.14.6"
  val http4s                   = "0.23.23"
  val logbackClassic           = "1.2.12"
}

lazy val automatedConfigurationRoot = project
  .in(file("."))
  .settings(
    version := "0.0.1",
    commonSettings
  )
  .aggregate(common, resourceListener, registry, smart, app, `web-handling`)

lazy val common = project
  .in(file("common"))
  .settings(
    name := "common",
    commonSettings,
    libraryDependencies += "org.typelevel"              %% "cats-core"     % Version.cats,
    libraryDependencies += "io.circe"                   %% "circe-core"    % Version.circe,
    libraryDependencies += "io.circe"                   %% "circe-generic" % Version.circe,
    libraryDependencies += "io.circe"                   %% "circe-parser"  % Version.circe,
    libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
    libraryDependencies ++= testDependencies
  )

lazy val resourceListener = project
  .settings(
    name := "resource-listener",
    commonSettings
  )
  .dependsOn(common, smart)

lazy val registry = project
  .settings(
    name := "registry",
    commonSettings
  )
  .dependsOn(common)

lazy val smart = project
  .settings(
    name := "smart",
    commonSettings,
    libraryDependencies ++= akkaDependencies,
    libraryDependencies ++= testDependencies,
    libraryDependencies ++= Seq(
      "com.typesafe.akka"          %% "akka-persistence-typed"      % Version.akka,
      "com.typesafe.akka"          %% "akka-serialization-jackson"  % Version.akka,
      "com.typesafe.akka"          %% "akka-persistence-testkit"    % Version.akka % Test,
      "ch.qos.logback"              % "logback-classic"             % Version.logbackClassic,
      "org.slf4j"                   % "slf4j-api"                   % "1.7.36",
      "org.slf4j"                   % "slf4j-simple"                % "1.7.36",
      "org.fusesource.leveldbjni"   % "leveldbjni-all"              % "1.8",
      "com.typesafe.akka"          %% "akka-persistence-query"      % Version.akka,
      "org.postgresql"              % "postgresql"                  % "42.4.3",
      "org.scalactic"              %% "scalactic"                   % "3.2.17",
      "com.geteventstore"          %% "akka-persistence-eventstore" % "8.0.0",
      "com.github.fd4s"            %% "fs2-kafka"                   % "2.5.0",
      "com.typesafe.scala-logging" %% "scala-logging"               % "3.9.5"
    )
  )
  .dependsOn(common)

lazy val app = project
  .dependsOn(smart, common, `web-handling`, resourceListener)
  .settings(
    name := "app",
    commonSettings,
    libraryDependencies += "org.http4s"    %% "http4s-dsl"                    % Version.http4s,
    libraryDependencies += "org.http4s"    %% "http4s-ember-client"           % Version.http4s,
    libraryDependencies += "org.http4s"    %% "http4s-ember-server"           % Version.http4s,
    libraryDependencies += "org.http4s"    %% "http4s-circe"                  % Version.http4s,
    libraryDependencies += "io.circe"      %% "circe-literal"                 % Version.circe,
    libraryDependencies += "org.scalatest" %% "scalatest"                     % Version.scalaTest % Test,
    libraryDependencies += "org.typelevel" %% "cats-effect-testing-scalatest" % "1.4.0"           % Test
  )

lazy val `web-handling` = project
  .dependsOn(smart, common)
  .settings(
    name := "web-handling",
    commonSettings
  )

lazy val commonSettings = Seq(
  // To make the default compiler and REPL use Dotty
  scalaVersion := Version.scala3
)

lazy val akkaDependencies = Seq(
  ("com.typesafe.akka" %% "akka-cluster-typed"       % Version.akka),
  ("com.typesafe.akka" %% "akka-actor-testkit-typed" % Version.akka % Test)
)

val testDependencies = Seq(
  "org.scalatest"          %% "scalatest"                  % Version.scalaTest,
  "org.scala-lang.modules" %% "scala-parallel-collections" % Version.scalaParallelCollections
).map(_ % Test)

//----------------------------------
// sbt-updates plugin configuration
//----------------------------------
// import sbt._
// do not suggest upgrades to the BSL (Business Source License) versions of Akka
dependencyUpdatesFilter -=
  moduleFilter(
    organization = "com.typesafe.akka",
    name = "akka-*",
    revision = "2.7.*" | "2.8.*"
  )
