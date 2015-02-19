import sbt._
import Keys._

import sbtassembly.Plugin._
import AssemblyKeys._
import spray.revolver.RevolverPlugin._


object MultiScaladocBuild extends Build {
  lazy val root =
    Project("root", file("."))
      .aggregate(plugin, core)

  lazy val plugin =
    Project("multidoc-plugin", file("plugin"))
      .settings(basicSettings: _*)
      .settings(
        sbtPlugin := true
      )
      .dependsOn(core)

  lazy val core =
    Project("multidoc-core", file("core"))
      .settings(basicSettings: _*)
      .settings(Revolver.settings: _*)
      .settings(assemblySettings: _*)
      .settings(
        scalaVersion in ThisBuild := "2.10.2"
      )
      .settings(
        resolvers ++= Seq(
          "spray" at "http://repo.spray.io/"
        ),
        libraryDependencies ++= {
          val akkaV = "2.3.8"
          val sprayV = "1.3.2"
          Seq(
            "com.typesafe.akka" %% "akka-actor" % akkaV,
            "io.spray" %% "spray-routing" % sprayV,
            "io.spray" %% "spray-can" % sprayV,
            "io.spray" %% "spray-json" % "1.3.0",
            // RUNTIME
            "com.typesafe.akka" %% "akka-slf4j" % akkaV % "runtime",
            "ch.qos.logback" % "logback-classic" % "1.0.13" % "runtime",
            // TEST
            "org.specs2" %% "specs2" % "2.4.2" % "test"
          )
        }
      )

  def basicSettings =
    Seq(
      organization := "net.virtual-void",
      version := "0.1.0"
    )
}
