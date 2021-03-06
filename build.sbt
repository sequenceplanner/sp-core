import SPSettings._

lazy val projectName = "sp-core"
lazy val projectVersion = "0.9.10"


lazy val spDep = Def.setting(Seq(
  PublishingSettings.orgNameFull %%% "sp-domain" % "0.9.10",
  PublishingSettings.orgNameFull %%% "sp-comm" % "0.9.11"
))

lazy val buildSettings = Seq(
  name         := projectName,
  description  := "The core service used in sp",
  version      := projectVersion,
  libraryDependencies ++= domainDependencies.value,
  libraryDependencies ++= commDependencies.value,
  libraryDependencies ++= spDep.value,
  scmInfo := Some(ScmInfo(
    PublishingSettings.githubSP(projectName),
    PublishingSettings.githubscm(projectName)
    )
  )
)


lazy val spcore = project.in(file("."))
  .settings(defaultBuildSettings)
  .settings(buildSettings)
  .settings(libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-persistence" % versions.akka,
    "com.typesafe.akka" %% "akka-persistence-query" % versions.akka,
    "org.iq80.leveldb"            % "leveldb"          % "0.7",
    "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8",
    "com.typesafe.akka" %% "akka-http-core" % "10.0.7",
    "com.typesafe.akka" %% "akka-http" % "10.0.7",
    "com.typesafe.akka" %% "akka-http-testkit" % "10.0.7"
  ))
