package cz.idealiste.idealvoting.server

import cz.idealiste.ideal.voting.server
import monocle.Monocle._
import pprint.PPrinter.BlackWhite
import zio._
import zio.config._
import zio.config.magnolia.DeriveConfigDescriptor
import zio.config.typesafe._
import zio.doobie.liquibase._
import zio.logging.Logger

final case class Config(
    dbTransactor: ZIODoobieLiquibase.Config,
    httpServer: HttpServer.Config,
    voting: VotingLive.Config,
)

object Config {

  implicit lazy val configDescriptor: ConfigDescriptor[Config] = DeriveConfigDescriptor.descriptor[Config]

  private[server] def layer(args: List[String]) = (
    for {
      logger <- ZIO.service[Logger[String]]
      typesafe = TypesafeConfigSource.fromResourcePath
      env = ConfigSource.fromSystemEnv()
      cmd = ConfigSource.fromCommandLineArgs(args)
      source = cmd <> env <> typesafe
      config <- read(implicitly[ConfigDescriptor[Config]].from(source)).orDie
      configSanitized = config.focus(_.dbTransactor.hikari.password).replace(Some("******"))
      () <- logger.info(s"${server.BuildInfo}, configuration:\n${BlackWhite(configSanitized)}")
    } yield config
  ).toLayer
}
