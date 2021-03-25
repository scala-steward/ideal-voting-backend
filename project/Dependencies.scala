import sbt._

object Dependencies {

  object Versions {

    val doobie = "0.12.1"
    val circe = "0.13.0"
    val http4s = "0.21.20"
    val liquibase = "4.3.1"
    val logback = "1.2.3"
    val mariadb = "2.7.2"
    val snakeyaml = "1.28"
    val zio = "1.0.5"
    val zioCats = "2.3.1.0"

    // Test
    val testcontainers = "0.39.3"
  }

  val doobie = "org.tpolecat" %% "doobie-hikari" % Versions.doobie
  val circe = "io.circe" %% "circe-generic" % Versions.circe
  val http4sCirce = "org.http4s" %% "http4s-circe" % Versions.http4s
  val http4sDsl = "org.http4s" %% "http4s-dsl" % Versions.http4s
  val http4sServer = "org.http4s" %% "http4s-blaze-server" % Versions.http4s
  val liquibase = "org.liquibase" % "liquibase-core" % Versions.liquibase
  val logback = "ch.qos.logback" % "logback-classic" % Versions.logback
  val mariadb = "org.mariadb.jdbc" % "mariadb-java-client" % Versions.mariadb
  val snakeyaml = "org.yaml" % "snakeyaml" % Versions.snakeyaml
  val zio = "dev.zio" %% "zio" % Versions.zio
  val zioCats = "dev.zio" %% "zio-interop-cats" % Versions.zioCats
  val zioTest = "dev.zio" %% "zio-test" % Versions.zio
  val zioTestSbt = "dev.zio" %% "zio-test-sbt" % Versions.zio

  // Test
  val testcontainers = "com.dimafeng" %% "testcontainers-scala-postgresql" % Versions.testcontainers
}
