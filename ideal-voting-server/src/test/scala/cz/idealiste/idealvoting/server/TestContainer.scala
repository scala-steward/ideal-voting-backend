package cz.idealiste.idealvoting.server

import com.dimafeng.testcontainers.DockerComposeContainer._
import com.dimafeng.testcontainers.{DockerComposeContainer, ExposedService}
import zio._
import zio.blocking.{Blocking, effectBlocking}

import java.io.File

object TestContainer {
  type DockerCompose = Has[DockerComposeContainer]

  def dockerCompose: ZLayer[Blocking, Nothing, DockerCompose] = {
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
}
