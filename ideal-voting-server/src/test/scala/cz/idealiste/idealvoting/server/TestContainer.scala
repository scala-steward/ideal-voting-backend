package cz.idealiste.idealvoting.server

import cats.implicits._
import com.dimafeng.testcontainers.DockerComposeContainer._
import com.dimafeng.testcontainers.{DockerComposeContainer, ExposedService}
import monocle.syntax.all._
import pprint.PPrinter.BlackWhite
import zio._
import zio.blocking.Blocking
import zio.logging.Logger
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

  private[server] lazy val layer = (
    for {
      docker <- ZIO.service[DockerComposeContainer]
      config0 <- ZIO.service[Config]
      logger <- ZIO.service[Logger[String]]
      (host, port) <- docker.getHostAndPort("mariadb_1")(3306).orDie
      config = config0.focus(_.dbTransactor.url).replace(show"jdbc:mariadb://$host:$port/idealvoting")
      _ <- logger.info(s"Modified test configuration:\n${BlackWhite(config)}.")
    } yield config
  ).toLayer

}
