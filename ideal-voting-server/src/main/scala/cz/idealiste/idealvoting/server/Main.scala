package cz.idealiste.idealvoting.server

import cz.idealiste.idealvoting.server.Config
import org.http4s.server.*
import zio.*
import zio.doobie.liquibase.ZIODoobieLiquibase
import zio.logging.backend.SLF4J

object Main extends ZIOAppDefault {

  override def run: ZIO[ZIOAppArgs, Throwable, Nothing] =
    serverLayer.launch

  private[server] val serverLayer =
    ZLayer.makeSome[ZIOAppArgs, Server](
      ZLayer.succeedEnvironment(DefaultServices.live),
      Runtime.removeDefaultLoggers,
      SLF4J.slf4j,
      Config.layer,
      httpLayer,
      HttpServerBlaze.Config.layer,
      HttpServerBlaze.layer,
      HttpServer.Server.layer,
    )

  private[server] lazy val httpLayer =
    ZLayer.makeSome[Clock & Random & Config, HttpApp](
      DbDoobie.Transactor.layer,
      ZIODoobieLiquibase.layer,
      DbDoobie.layer,
      VotingSystemDummy.layer,
      VotingLive.Config.layer,
      VotingLive.layer,
      HandlerLive.layer,
      HttpAppLive.layer,
    )
}
