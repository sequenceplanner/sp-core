import SPSettings._
import sbt.ScmInfo

lazy val projectName = "sp-core"
lazy val projectVersion = "0.9.10"

version := "0.1"
scalaVersion := "2.12.6"



// The Play project itself
lazy val root = (project in file("."))
  .enablePlugins(PlayService)
  .settings(defaultBuildSettings)
  .settings(
    name := projectName,
    version := projectVersion,
    libraryDependencies ++= spDependencies ++ dependencies,
    scmInfo := Some(ScmInfo(
      PublishingSettings.githubSP(projectName),
      PublishingSettings.githubscm(projectName)
    ))
  )

