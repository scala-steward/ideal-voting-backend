package cz.idealiste.idealvoting.server

import org.http4s.server._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.magic._
import zio.random.Random

object Main extends App {

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    serverLayer.build.useForever.exitCode

  lazy val serverLayer: RLayer[Blocking with Clock with Random, Has[Server[Task]]] =
    ZLayer.fromSomeMagic[Blocking with Clock with Random, Has[Server[Task]]](
      Config.layer,
      httpLayer,
      Config.HttpServer.layer,
      HttpServer.layer,
    )

  lazy val httpLayer: RLayer[Blocking with Clock with Random with Has[Config], Has[Http]] =
    ZLayer.fromSomeMagic[Blocking with Clock with Random with Has[Config], Has[Http]](
      Config.DbTransactor.layer,
      DbTransactor.layer,
      Db.layer,
      Config.Voting.layer,
      Voting.layer,
      Http.layer,
    )
}
