package cz.idealiste.idealvoting.server

import org.http4s.server._
import zio._
import zio.blocking.Blocking
import zio.magic._
import zio.random.Random

object Main extends App {

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    serverLayer.build.useForever.exitCode

  lazy val serverLayer: RLayer[ZEnv, Has[Server[Task]]] =
    ZLayer.fromSomeMagic[ZEnv, Has[Server[Task]]](
      Config.layer,
      httpLayer,
      Config.HttpServer.layer,
      HttpServer.layer,
    )

  lazy val httpLayer: RLayer[Blocking with Random with Has[Config], Has[Http]] =
    ZLayer.fromSomeMagic[Blocking with Random with Has[Config], Has[Http]](
      Config.DbTransactor.layer,
      DbTransactor.layer,
      Db.layer,
      Config.Voting.layer,
      Voting.layer,
      Http.layer,
    )
}
