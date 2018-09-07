import SPSettings._

lazy val projectName = "sp-core"
lazy val projectVersion = "0.11.0"


lazy val spDep = Def.setting(Seq(
  PublishingSettings.orgNameFull %%% "sp-domain" % "0.9.12"
))

lazy val buildSettings = Seq(
  name         := projectName,
  description  := "The core of SP",
  version      := projectVersion,
  libraryDependencies ++= domainDependencies.value,
  libraryDependencies ++= spDep.value,
  scmInfo := Some(ScmInfo(
    PublishingSettings.githubSP(projectName),
    PublishingSettings.githubscm(projectName)
    )
  )
)


lazy val shared = crossProject.crossType(CrossType.Pure).in(file("shared"))
  .settings(
    defaultBuildSettings,
    buildSettings
  )

lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js

lazy val server = project.in(file("server"))
  .settings(defaultBuildSettings)
  .settings(buildSettings)
  .dependsOn(sharedJvm)
  .settings(libraryDependencies ++= SPSettings.commDependencies.value)
  .settings(libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-persistence" % versions.akka,
    "com.typesafe.akka" %% "akka-persistence-query" % versions.akka,
    "org.iq80.leveldb"            % "leveldb"          % "0.7",
    "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8",
    "com.typesafe.akka" %% "akka-http-core" % "10.0.7",
    "com.typesafe.akka" %% "akka-http" % "10.0.7",
    "com.typesafe.akka" %% "akka-http-testkit" % "10.0.7"
  ))


lazy val client = project.in(file("client"))
  .settings(defaultBuildSettings)
  .settings(buildSettings)
  .dependsOn(sharedJs)
  .settings(
    jsSettings,
    libraryDependencies ++= guiDependencies.value
  )
  .enablePlugins(ScalaJSPlugin)



//lazy val root = project.in(file("."))
//  .aggregate(spgui_jvm, spgui_js)
//  .settings(defaultBuildSettings)
//  .settings(buildSettings)
//  .settings(
//    publish              := {},
//    publishLocal         := {},
//    publishArtifact      := false,
//    Keys.`package`       := file("")
//  )
