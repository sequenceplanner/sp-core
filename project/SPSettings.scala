import sbt._
import Keys._
import com.typesafe.sbt.SbtPgp.autoImport._
import play.sbt.PlayImport.{akkaHttpServer, filters}
import xerial.sbt.Sonatype.autoImport._

object SPSettings {
  object versions {
    val scala = "2.12.6"
    val akka = "2.5.14"
    val levelDbPort = "0.7"
    val levelDbJni = "1.8"
  }

  val spDependencies = Seq(
    PublishingSettings.orgNameFull %% "sp-domain" % "0.9.12",
    PublishingSettings.orgNameFull %% "sp-comm" % "0.9.11"
  )

  val dependencies = Seq(
    akkaHttpServer,
    filters,
    "com.typesafe.akka" %% "akka-cluster" % versions.akka,
    "com.typesafe.akka" %% "akka-cluster-tools" % versions.akka,
    "com.typesafe.akka" %% "akka-persistence" % versions.akka,
    "com.typesafe.akka" %% "akka-persistence-query" % versions.akka,

    "org.iq80.leveldb"            % "leveldb"          % versions.levelDbPort,
    "org.fusesource.leveldbjni"   % "leveldbjni-all"   % versions.levelDbJni
  )

  lazy val compilerOptions = Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-language:higherKinds",
    "-Ypartial-unification"
  )

  lazy val repoResolvers: Seq[Resolver] = Seq(
    Resolver.sonatypeRepo("public"),
    Resolver.typesafeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  )

  lazy val defaultBuildSettings = Seq(
    organization := PublishingSettings.orgNameFull,
    homepage     := Some(PublishingSettings.githubSP()),
    licenses     := PublishingSettings.mitLicense,
    scalaVersion := versions.scala,
    scalacOptions ++= compilerOptions,
    resolvers ++= repoResolvers,
    useGpg := true,
    publishArtifact in Test := false,
    publishMavenStyle := true,
    publishTo := PublishingSettings.pubTo.value,
    pomIncludeRepository := { _ => false },
    sonatypeProfileName := PublishingSettings.groupIdSonatype,
    developers := ProjectSettings.developers
  )
}
