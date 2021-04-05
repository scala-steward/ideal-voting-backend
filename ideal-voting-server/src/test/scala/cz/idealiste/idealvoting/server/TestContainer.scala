package cz.idealiste.idealvoting.server

import cats.implicits._
import com.dimafeng.testcontainers.DockerComposeContainer._
import com.dimafeng.testcontainers.{DockerComposeContainer, ExposedService}
import zio._
import zio.blocking.{Blocking, effectBlocking}

import java.io.File

object TestContainer {

  lazy val dockerCompose: URLayer[Blocking, Has[DockerComposeContainer]] = {
    val container = new DockerComposeContainer(
      new File("docker-compose.yml"),
      List(
        ExposedService("mariadb_1", 3306),
        ExposedService("mailhog_1", 1025),
        ExposedService("mailhog_1", 8025),
      ),
    )
    ZManaged
      .make(effectBlocking(container.start()).as(container).orDie)(container =>
        effectBlocking(container.stop()).orDie,
      )
      .toLayer
  }

  lazy val config: URLayer[Has[Config] with Has[DockerComposeContainer], Has[Config]] = (
    for {
      config0 <- ZIO.service[Config]
      docker <- ZIO.service[DockerComposeContainer]
      config = config0.copy(dbTransactor =
        config0.dbTransactor.copy(
          url =
            show"jdbc:mysql://${docker.getServiceHost("mariadb_1", 3306)}:${docker.getServicePort("mariadb_1", 3306)}/idealvoting",
        ),
      )
    } yield config
  ).toLayer
}
