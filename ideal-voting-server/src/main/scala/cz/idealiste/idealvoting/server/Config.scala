package cz.idealiste.idealvoting.server

import cz.idealiste.ideal.voting.server
import monocle.Monocle._
import pprint.PPrinter.BlackWhite
import zio._
import zio.config.magnolia._
import zio.config.typesafe._
import zio.doobie.liquibase._

final case class Config(
    dbTransactor: ZIODoobieLiquibase.Config,
    httpServer: HttpServerBlaze.Config,
    voting: VotingLive.Config,
)

object Config {

  implicit lazy val configDescriptor: DeriveConfig[Config] = DeriveConfig.getDeriveConfig[Config]

  private[server] val layer = ZLayer.fromZIO {
    val typesafe = TypesafeConfigProvider.fromResourcePath()
    val env = ConfigProvider.envProvider
    val source = env.orElse(typesafe)
    for {
      config <- source.load(deriveConfig[Config]).orDie
      configSanitized = config.focus(_.dbTransactor.hikari.password).replace(Some("******"))
      () <- ZIO.logInfo(s"${server.BuildInfo}, configuration:\n${BlackWhite(configSanitized)}")
    } yield config
  }
}
