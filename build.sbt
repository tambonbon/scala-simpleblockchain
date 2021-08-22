version := "0.1"

scalaVersion := "2.13.6"

lazy val root = (project in file("."))
    .settings(
        name := "scala-simpleblockchain",
        libraryDependencies ++= Seq(
            "org.scalatest" %% "scalatest" % "3.2.9",
            "io.spray" %% "spray-json" % "1.3.5",
            "com.typesafe.akka" %% "akka-actor" % "2.6.16"
        )
    )