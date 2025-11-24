import sbt._

object Dependencies {

  object Versions {

    val chimney = "1.8.2"
    val circe = "0.14.15"
    val commonsLang = "3.20.0"
    val emil = "0.19.0"
    val http4s = "0.23.33"
    val jackson = "2.20.1"
    val liquibaseDoobie = "3.2.0"
    val liquibaseSlf4j = "5.1.0"
    val logback = "1.5.21"
    val logbackJackson = "0.1.5"
    val logbackJson = "0.1.5"
    val mariadb = "3.5.6"
    val monocle = "3.3.0"
    val pprint = "0.9.5"
    val snakeyaml = "2.5"
    val zio = "2.1.22"
    val zioConfigTypesafe = "4.0.5"
    val zioLoggingSlf4j = "2.5.1"

    // Test
    val zioTestcontainers = "0.8.0"

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
  val http4sServerEmber = "org.http4s" %% "http4s-ember-server" % Versions.http4s
  val jackson = "com.fasterxml.jackson.core" % "jackson-databind" % Versions.jackson
  val liquibaseDoobieZio = "com.github.sideeffffect" %% "liquibase-doobie-zio" % Versions.liquibaseDoobie
  val liquibaseDoobieZioConfig = "com.github.sideeffffect" %% "liquibase-doobie-zio-config" % Versions.liquibaseDoobie
  val liquibaseSlf4j = "com.mattbertolini" % "liquibase-slf4j" % Versions.liquibaseSlf4j
  val logback = "ch.qos.logback" % "logback-classic" % Versions.logback
  val logbackJackson = "ch.qos.logback.contrib" % "logback-jackson" % Versions.logbackJackson
  val logbackJson = "ch.qos.logback.contrib" % "logback-json-classic" % Versions.logbackJson
  val mariadb = "org.mariadb.jdbc" % "mariadb-java-client" % Versions.mariadb
  val monocle = "dev.optics" %% "monocle-macro" % Versions.monocle
  val pprint = "com.lihaoyi" %% "pprint" % Versions.pprint
  val snakeyaml = "org.yaml" % "snakeyaml" % Versions.snakeyaml
  val zioConfigTypesafe = "dev.zio" %% "zio-config-typesafe" % Versions.zioConfigTypesafe
  val zioLoggingSlf4j = "dev.zio" %% "zio-logging-slf4j" % Versions.zioLoggingSlf4j
  val zioTest = "dev.zio" %% "zio-test" % Versions.zio
  val zioTestSbt = "dev.zio" %% "zio-test-sbt" % Versions.zio

  // Test
  val zioTestcontainers = "com.github.sideeffffect" %% "zio-testcontainers" % Versions.zioTestcontainers

}
