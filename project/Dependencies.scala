import sbt._

object Dependencies {

  object Versions {

    val chimney = "0.8.5"
    val circe = "0.14.6"
    val commonsLang = "3.14.0"
    val emil = "0.17.0"
    val http4s = "0.23.26"
    val http4sBlaze = "0.23.16"
    val jackson = "2.17.0"
    val liquibaseSlf4j = "5.0.0"
    val logback = "1.5.6"
    val logbackJackson = "0.1.5"
    val logbackJson = "0.1.5"
    val mariadb = "3.3.3"
    val monocle = "3.2.0"
    val pprint = "0.9.0"
    val snakeyaml = "2.0"
    val zio = "2.0.22"
    val zioDoobie = "0.6.0"
    val zioLoggingSlf4j = "2.2.3"

    // Test
    val testcontainers = "0.40.17"
    val zioTestcontainers = "0.5.0"

  }

  val chimney = "io.scalaland" %% "chimney-cats" % Versions.chimney
  val circeGeneric = "io.circe" %% "circe-generic" % Versions.circe
  val circeParser = "io.circe" %% "circe-parser" % Versions.circe
  val commonsLang = "org.apache.commons" % "commons-lang3" % Versions.commonsLang
  val emil = "com.github.eikek" %% "emil-doobie" % Versions.emil
  val http4sCirce = "org.http4s" %% "http4s-circe" % Versions.http4s
  val http4sClient = "org.http4s" %% "http4s-client" % Versions.http4s
  val http4sDsl = "org.http4s" %% "http4s-dsl" % Versions.http4s
  val http4sServer = "org.http4s" %% "http4s-server" % Versions.http4s
  val http4sServerBlaze = "org.http4s" %% "http4s-blaze-server" % Versions.http4sBlaze
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
  val zioTest = "dev.zio" %% "zio-test" % Versions.zio
  val zioTestSbt = "dev.zio" %% "zio-test-sbt" % Versions.zio

  // Test
  val testcontainers = "com.dimafeng" %% "testcontainers-scala-core" % Versions.testcontainers
  val zioTestcontainers = "com.github.sideeffffect" %% "zio-testcontainers" % Versions.zioTestcontainers

}
