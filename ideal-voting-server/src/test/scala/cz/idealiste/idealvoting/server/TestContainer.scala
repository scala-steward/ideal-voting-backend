package cz.idealiste.idealvoting.server

import cats.implicits._
import com.dimafeng.testcontainers.DockerComposeContainer._
import com.dimafeng.testcontainers.{DockerComposeContainer, ExposedService}
import zio._
import zio.blocking.Blocking
import zio.logging.{Logger, Logging}
import zio.testcontainers._

import java.io.File

object TestContainer {

  lazy val dockerCompose: RLayer[Blocking, Has[DockerComposeContainer]] =
    new DockerComposeContainer(
      new File("docker-compose.yml"),
      List(
        ExposedService("mariadb_1", 3306),
        ExposedService("mailhog_1", 1025),
        ExposedService("mailhog_1", 8025),
      ),
    ).toLayer

  def make(docker: DockerComposeContainer, config: Config, logger: Logger[String]): UIO[Config] =
    docker
      .getHostAndPort("mariadb_1")(3306)
      .flatMap { case (host, port) =>
        val c = config.copy(dbTransactor = config.dbTransactor.copy(url = show"jdbc:mariadb://$host:$port/idealvoting"))
        logger.info(s"Modified test configuration: $c.").as(c)
      }
      .orDie

  lazy val layer: URLayer[Has[Config] with Has[DockerComposeContainer] with Logging, Has[Config]] = (
    for {
      docker <- ZIO.service[DockerComposeContainer]
      config <- ZIO.service[Config]
      logger <- ZIO.service[Logger[String]]
      config <- make(docker, config, logger)
    } yield config
  ).toLayer

}
