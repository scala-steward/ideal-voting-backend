package cz.idealiste.idealvoting.server

import cats.implicits._
import com.dimafeng.testcontainers.{DockerComposeContainer, ExposedService}
import cz.idealiste.idealvoting.server.Config
import monocle.syntax.all._
import pprint.PPrinter.BlackWhite
import zio._
import zio.testcontainers._

import java.io.File

object TestContainer {

  private[server] lazy val dockerCompose = ZLayer.fromTestContainer {
    new DockerComposeContainer(
      new File("docker-compose.yml"),
      List(
        ExposedService("mariadb", 3306),
        ExposedService("mailhog", 1025),
        ExposedService("mailhog", 8025),
      ),
      localCompose = false,
    )
  }

  private[server] lazy val layer = ZLayer.fromZIO {
    for {
      docker <- ZIO.service[DockerComposeContainer]
      (host, port) <- docker.getHostAndPort("mariadb")(3306)
      config0 <- ZIO.service[Config]
      config = config0.focus(_.dbTransactor.hikari.jdbcUrl).replace(show"jdbc:mariadb://$host:$port/idealvoting")
      _ <- ZIO.logInfo(s"Modified test configuration:\n${BlackWhite(config)}.")
    } yield config
  }

}
