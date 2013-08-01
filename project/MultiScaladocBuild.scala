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
        resolvers += "nightly spray" at "http://nightlies.spray.io/",

        libraryDependencies ++= {
          val akkaV = "2.2.0"
          val sprayV = "1.2-20130801"
          Seq(
            "com.typesafe.akka" %% "akka-actor" % akkaV,
            "io.spray" % "spray-routing" % sprayV,
            "io.spray" % "spray-can" % sprayV,
            "io.spray" %% "spray-json" % "1.2.5",
            // RUNTIME
            "com.typesafe.akka" %% "akka-slf4j" % akkaV % "runtime",
            "ch.qos.logback" % "logback-classic" % "1.0.0" % "runtime",
            // TEST
            "org.specs2" %% "specs2" % "1.13" % "test"
          )
        },

        Revolver.reStartArgs ++= Seq(
          "spray-json:/home/johannes/.ivy2/local/io.spray/spray-json_2.10/1.2.5/docs/spray-json_2.10-javadoc.jar",
          "scala-library:/home/johannes/.ivy2/cache/org.scala-lang/scala-library/docs/scala-library-2.10.2-javadoc.jar",
          "parboiled-scala:/home/johannes/.ivy2/cache/org.parboiled/parboiled-scala_2.10/docs/parboiled-scala_2.10-1.1.5-javadoc.jar"
        )
      )

  def basicSettings = ScalariformSupport.formatSettings ++
    seq(
      organization := "net.virtual-void",
      version := "0.1.0"
    )
}
