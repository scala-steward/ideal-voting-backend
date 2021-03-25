Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / turbo := true
ThisBuild / scalaVersion := "2.13.5"

// https://github.com/olafurpg/sbt-ci-release/issues/181
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"

lazy val root = project
  .in(file("."))
  .settings(commonSettings)
  .settings(
    name := "ideal-voting-backend",
    publish / skip := true,
  )
  .aggregate(server)

lazy val server = project
  .in(file("ideal-voting-server"))
  .settings(commonSettings)
  .settings(
    name := "ideal-voting-server",
    Compile / mainClass := Some("cz.idealiste.idealvoting.server.Main"),
    libraryDependencies ++= List(
      Dependencies.doobie,
      Dependencies.circe,
      Dependencies.http4sCirce,
      Dependencies.http4sDsl,
      Dependencies.http4sServer,
      Dependencies.liquibase,
      Dependencies.logback,
      Dependencies.mariadb,
      Dependencies.snakeyaml,
      Dependencies.zio,
      Dependencies.zioCats,
      // Test
      Dependencies.testcontainers % Test,
      Dependencies.zioTest % Test,
      Dependencies.zioTestSbt % Test,
    ),
  )

lazy val commonSettings: List[Def.Setting[_]] = List(
  organization := "cz.idealiste",
  homepage := Some(url("https://github.com/Idealiste-cz/ideal-voting-backend")),
  licenses := List("AGPLv3" -> url("https://www.gnu.org/licenses/agpl-3.0.en.html")),
  developers := List(
    Developer(
      "sideeffffect",
      "Ondra Pelech",
      "ondra.pelech@gmail.com",
      url("https://github.com/sideeffffect"),
    ),
  ),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  missinglinkExcludedDependencies ++= List(
    moduleFilter(organization = "ch.qos.logback", name = "logback-classic"),
    moduleFilter(organization = "ch.qos.logback", name = "logback-core"),
    moduleFilter(organization = "com.zaxxer", name = "HikariCP"),
    moduleFilter(organization = "org.slf4j", name = "slf4j-api"),
  ),
  mimaReportBinaryIssues := {},
) ++ DecentScala.decentScalaSettings

addCommandAlias("ci", "; check; publishLocal")
