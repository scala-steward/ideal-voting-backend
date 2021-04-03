package cz.idealiste.idealvoting.server

import org.http4s.server._
import zio._
import zio.magic._

object Main extends App {

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    serverLayer.launch.exitCode

  lazy val serverLayer: TaskLayer[Has[Server[Task]]] =
    ZLayer.fromMagic[Has[Server[Task]]](
      ZEnv.live,
      Config.layer,
      Config.DbTransactor.layer,
      DbTransactor.layer,
      Db.layer,
      Config.Voting.layer,
      Voting.layer,
      Http.layer,
      Config.HttpServer.layer,
      HttpServer.layer,
    )
}
