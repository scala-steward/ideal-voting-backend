package cz.idealiste.idealvoting.server

import com.dimafeng.testcontainers.DockerComposeContainer._
import com.dimafeng.testcontainers.{DockerComposeContainer, ExposedService}
import zio._
import zio.blocking.{Blocking, effectBlocking}

import java.io.File

object TestContainer {
  type DockerCompose = Has[DockerComposeContainer]

  def dockerCompose: ZLayer[Blocking, Nothing, DockerCompose] =
    ZManaged.make {
      effectBlocking {
        val container = new DockerComposeContainer(
          new File("docker-compose.yml"),
          List(ExposedService("mariadb", 3306)),
        )
        container.start()
        container
      }.orDie
    }(container => effectBlocking(container.stop()).orDie).toLayer
}
