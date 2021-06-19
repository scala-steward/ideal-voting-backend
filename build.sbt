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
      Dependencies.logback,
      Dependencies.mariadb,
      Dependencies.snakeyaml,
      Dependencies.zio,
      Dependencies.zioDoobieLiquibase,
      Dependencies.zioCats,
      Dependencies.zioConfig,
      Dependencies.zioConfigMagnolia,
      Dependencies.zioMagic,
      // Test
      Dependencies.zioTest % Test,
      Dependencies.zioTestcontainers % Test,
      Dependencies.zioTestSbt % Test,
    ),
  )

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
  mimaReportBinaryIssues := {},
  // https://github.com/olafurpg/sbt-ci-release/issues/181
  sonatypeCredentialHost := "s01.oss.sonatype.org",
  sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
)

addCommandAlias("ci", "; check; +publishLocal")
