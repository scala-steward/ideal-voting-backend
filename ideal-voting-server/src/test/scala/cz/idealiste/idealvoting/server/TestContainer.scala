package cz.idealiste.idealvoting.server

import cats.implicits.*
import com.dimafeng.testcontainers.DockerComposeContainer.*
import com.dimafeng.testcontainers.{DockerComposeContainer, ExposedService}
import monocle.syntax.all.*
import pprint.PPrinter.BlackWhite
import zio.*
import zio.testcontainers.*

import java.io.File

object TestContainer {

  lazy val dockerCompose: TaskLayer[DockerComposeContainer] =
    new DockerComposeContainer(
      new File("docker-compose.yml"),
      List(
        ExposedService("mariadb_1", 3306),
        ExposedService("mailhog_1", 1025),
        ExposedService("mailhog_1", 8025),
      ),
    ).toLayer

  private[server] lazy val layer = ZLayer.fromZIO {
    for {
      docker <- ZIO.service[DockerComposeContainer]
      config0 <- ZIO.service[Config]
      (host, port) <- docker.getHostAndPort("mariadb_1")(3306)
      config = config0.focus(_.dbTransactor.hikari.jdbcUrl).replace(Some(show"jdbc:mariadb://$host:$port/idealvoting"))
      _ <- ZIO.logInfo(s"Modified test configuration:\n${BlackWhite(config)}.")
    } yield config
  }

}
