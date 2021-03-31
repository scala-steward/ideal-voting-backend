package cz.idealiste.idealvoting.server

import org.http4s.server._
import zio._
import zio.blocking.Blocking
import zio.random.Random

object Main extends App {

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    server.useForever.exitCode

  val server: RManaged[Blocking with Random, Server[Task]] = {

    for {
      dbTransactor <- DbTransactor.make(dbTransactorConfig)
      db = Db.make(dbTransactor)
      random <- Managed.access[Random](_.get)
      voting = Voting.make(votingConfig, db, random)
      http = Http.make(voting)
      httpServer <- HttpServer.make(httpServerConfig, http)
    } yield httpServer
  }

  private lazy val dbTransactorConfig =
    Config.DbTransactor("jdbc:mysql://localhost:3306/idealvoting", "idealvoting", "idealvoting")
  lazy val dbTransactorConfigLayer: ULayer[Has[Config.DbTransactor]] = ZLayer.succeed(dbTransactorConfig)

  private lazy val votingConfig = Config.Voting()
  lazy val votingConfigLayer: ULayer[Has[Config.Voting]] = ZLayer.succeed(votingConfig)

  private lazy val httpServerConfig = Config.HttpServer("localhost", 8080)
  lazy val httpServerConfigLayer: ULayer[Has[Config.HttpServer]] = ZLayer.succeed(httpServerConfig)

  lazy val serverLayer: TaskLayer[Has[Server[Task]]] =
    (((((Blocking.live ++ dbTransactorConfigLayer) >>> DbTransactor.layer >>> Db.layer) ++ Random.live ++ votingConfigLayer) >>> Voting.layer >>> Http.layer) ++ httpServerConfigLayer) >>> HttpServer.layer
}
