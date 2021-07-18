package cz.idealiste.idealvoting.server

import org.http4s.server._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.doobie.liquibase.ZIODoobieLiquibase
import zio.logging.slf4j.Slf4jLogger
import zio.magic._
import zio.random.Random
import zio.system.System

object Main extends App {

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    serverLayer(args).build.useForever.exitCode

  def serverLayer(
      args: List[String],
  ): RLayer[Blocking with Clock with Random with System, Has[Server[Task]]] =
    ZLayer.fromSomeMagic[Blocking with Clock with Random with System, Has[Server[Task]]](
      Config.layer(args),
      httpLayer,
      Config.HttpServer.layer,
      HttpServer.layer,
    )

  lazy val httpLayer: RLayer[Blocking with Clock with Random with Has[Config], Has[Http]] =
    ZLayer.fromSomeMagic[Blocking with Clock with Random with Has[Config], Has[Http]](
      Slf4jLogger.make((_, s) => s),
      Config.DbTransactor.layer,
      ZIODoobieLiquibase.layer,
      Db.layer,
      Config.Voting.layer,
      Voting.layer,
      Http.layer,
    )
}
