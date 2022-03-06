Global / onChangedBuildSource := ReloadOnSourceChanges

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
      Dependencies.circe,
      Dependencies.commonsLang,
      Dependencies.emil,
      Dependencies.http4sCirce,
      Dependencies.http4sDsl,
      Dependencies.http4sServer,
      Dependencies.jackson,
      Dependencies.liquibaseSlf4j % "runtime",
      Dependencies.logback,
      Dependencies.logbackJackson,
      Dependencies.logbackJson,
      Dependencies.mariadb,
      Dependencies.snakeyaml,
      Dependencies.zioDoobieLiquibase,
      Dependencies.zioLoggingSlf4j,
      Dependencies.zioMagic,
      // Test
      Dependencies.zioTest % Test,
      Dependencies.zioTestcontainers % Test,
      Dependencies.zioTestSbt % Test,
    ),
  )
  .enablePlugins(BuildInfoPlugin)

lazy val commonSettings: List[Def.Setting[_]] = DecentScala.decentScalaSettings ++ List(
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
  crossScalaVersions := List(DecentScala.decentScalaVersion213),
  ThisBuild / scalafixDependencies ++= List(
    Dependencies.zioMagicComments,
  ),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  missinglinkExcludedDependencies ++= List(
    moduleFilter(organization = "ch.qos.logback", name = "logback-classic"),
    moduleFilter(organization = "ch.qos.logback", name = "logback-core"),
    moduleFilter(organization = "com.zaxxer", name = "HikariCP"),
    moduleFilter(organization = "org.slf4j", name = "slf4j-api"),
  ),
  missinglinkIgnoreDestinationPackages ++= List(
    IgnoredPackage("java.sql"), // https://github.com/tpolecat/doobie/pull/1632
    IgnoredPackage("org.osgi.framework"),
  ),
  mimaReportBinaryIssues := {},
  // https://github.com/olafurpg/sbt-ci-release/issues/181
  sonatypeCredentialHost := "s01.oss.sonatype.org",
  sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
)

addCommandAlias("ci", "; check; +publishLocal")
