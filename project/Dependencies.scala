import sbt._

object Dependencies {

  object Versions {

    val chimney = "0.6.1"
    val circe = "0.14.1"
    val commonsLang = "3.12.0"
    val emil = "0.12.0"
    val http4s = "0.23.11"
    val jackson = "2.13.2.2"
    val liquibaseSlf4j = "4.0.0"
    val logback = "1.2.11"
    val logbackJackson = "0.1.5"
    val logbackJson = "0.1.5"
    val mariadb = "2.7.5"
    val monocle = "3.1.0"
    val pprint = "0.7.3"
    val snakeyaml = "1.30"
    val zio = "1.0.13"
    val zioDoobie = "0.2.0"
    val zioLoggingSlf4j = "0.5.14"
    val zioMagic = "0.3.11"

    // Test
    val zioTestcontainers = "0.3.0"

    // Scalafix plugins
    val zioMagicComments = "0.1.0"
  }

  val chimney = "io.scalaland" %% "chimney-cats" % Versions.chimney
  val circeGeneric = "io.circe" %% "circe-generic" % Versions.circe
  val circeParser = "io.circe" %% "circe-parser" % Versions.circe
  val commonsLang = "org.apache.commons" % "commons-lang3" % Versions.commonsLang
  val emil = "com.github.eikek" %% "emil-doobie" % Versions.emil
  val http4sCirce = "org.http4s" %% "http4s-circe" % Versions.http4s
  val http4sClient = "org.http4s" %% "http4s-client" % Versions.http4s
  val http4sDsl = "org.http4s" %% "http4s-dsl" % Versions.http4s
  val http4sServer = "org.http4s" %% "http4s-blaze-server" % Versions.http4s
  val jackson = "com.fasterxml.jackson.core" % "jackson-databind" % Versions.jackson
  val liquibaseSlf4j = "com.mattbertolini" % "liquibase-slf4j" % Versions.liquibaseSlf4j
  val logback = "ch.qos.logback" % "logback-classic" % Versions.logback
  val logbackJackson = "ch.qos.logback.contrib" % "logback-jackson" % Versions.logbackJackson
  val logbackJson = "ch.qos.logback.contrib" % "logback-json-classic" % Versions.logbackJson
  val mariadb = "org.mariadb.jdbc" % "mariadb-java-client" % Versions.mariadb
  val monocle = "dev.optics" %% "monocle-macro" % Versions.monocle
  val pprint = "com.lihaoyi" %% "pprint" % Versions.pprint
  val snakeyaml = "org.yaml" % "snakeyaml" % Versions.snakeyaml
  val zioDoobieLiquibase = "com.github.sideeffffect" %% "zio-doobie-liquibase" % Versions.zioDoobie
  val zioLoggingSlf4j = "dev.zio" %% "zio-logging-slf4j" % Versions.zioLoggingSlf4j
  val zioMagic = "io.github.kitlangton" %% "zio-magic" % Versions.zioMagic
  val zioTest = "dev.zio" %% "zio-test" % Versions.zio
  val zioTestSbt = "dev.zio" %% "zio-test-sbt" % Versions.zio

  // Test
  val zioTestcontainers = "com.github.sideeffffect" %% "zio-testcontainers" % Versions.zioTestcontainers

  // Scalafix plugins
  val zioMagicComments = "com.timushev" %% "zio-magic-comments" % Versions.zioMagicComments
}
