import Dependencies._

ThisBuild / scalaVersion     := "2.11.7"
ThisBuild / version          := "0.1.0"

lazy val root = (project in file("."))
  .settings(
    name := "scala-data-proc",
    scalaVersion := "2.11.7",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-library" % scalaVersion.value % "provided",
      "org.apache.spark" %% "spark-sql" % "2.4.0",
      scalaTest % Test
    )
  )
