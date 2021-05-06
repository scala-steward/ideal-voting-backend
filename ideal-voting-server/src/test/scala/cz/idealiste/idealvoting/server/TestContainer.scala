package cz.idealiste.idealvoting.server

import cats.implicits._
import com.dimafeng.testcontainers.DockerComposeContainer._
import com.dimafeng.testcontainers.{DockerComposeContainer, ExposedService}
import zio._
import zio.blocking.Blocking
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

  lazy val config: URLayer[Has[Config] with Has[DockerComposeContainer], Has[Config]] = (
    for {
      config0 <- ZIO.service[Config]
      docker <- ZIO.service[DockerComposeContainer]
      (host, port) <- docker.getHostAndPort("mariadb_1")(3306)
      config = config0.copy(dbTransactor =
        config0.dbTransactor.copy(url = show"jdbc:mysql://$host:$port/idealvoting"),
      )
      _ = println(config)
    } yield config
  ).orDie.toLayer

}
