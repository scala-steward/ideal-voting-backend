package cz.idealiste.idealvoting.server

import org.http4s.server._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.doobie.liquibase.ZIODoobieLiquibase
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger
import zio.magic._
import zio.random.Random
import zio.system.System

object Main extends App {

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    serverLayer(args).build.useForever.exitCode

  private[server] def serverLayer(args: List[String]) =
    ZLayer.fromSomeMagic[Blocking with Clock with Random with System, Has[Server]](
      Slf4jLogger.make((_, s) => s),
      Config.layer(args),
      httpLayer,
      HttpServer.Config.layer,
      HttpServer.layer,
    )

  private[server] lazy val httpLayer =
    ZLayer.fromSomeMagic[Blocking with Clock with Random with Has[Config] with Logging, Has[Http]](
      Db.Transactor.layer,
      ZIODoobieLiquibase.layer,
      Db.layer,
      VotingSystem.layer,
      Voting.Config.layer,
      Voting.layer,
      Http.layer,
    )
}
