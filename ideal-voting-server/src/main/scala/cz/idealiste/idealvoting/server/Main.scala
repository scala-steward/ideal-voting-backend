package cz.idealiste.idealvoting.server

import org.http4s.server._
import zio._
import zio.blocking.Blocking
import zio.random.Random

object Main extends App {

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    serverLayer.launch.exitCode

  lazy val serverLayer: TaskLayer[Has[Server[Task]]] =
    Config.layer >>>
      (((((Blocking.live ++ Config.DbTransactor.layer) >>> DbTransactor.layer >>> Db.layer)
        ++ Random.live ++ Config.Voting.layer) >>> Voting.layer >>>
        Http.layer) ++ Config.HttpServer.layer) >>> HttpServer.layer
}
