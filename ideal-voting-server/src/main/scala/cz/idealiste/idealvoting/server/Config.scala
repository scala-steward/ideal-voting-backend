package cz.idealiste.idealvoting.server

import cz.idealiste.ideal.voting.server
import monocle.Monocle.*
import pprint.PPrinter.BlackWhite
import zio.*
import zio.config.*
import zio.config.magnolia.Descriptor
import zio.config.typesafe.*
import zio.doobie.liquibase.*

final case class Config(
    dbTransactor: ZIODoobieLiquibase.Config,
    httpServer: HttpServerBlaze.Config,
    voting: VotingLive.Config,
)

object Config {

  implicit lazy val configDescriptor: ConfigDescriptor[Config] = Descriptor.descriptor[Config]

  private[server] val layer = ZLayer.fromZIO {
    for {
      args <- ZIO.service[ZIOAppArgs]
      typesafe = TypesafeConfigSource.fromResourcePath
      env = ConfigSource.fromSystemEnv()
      cmd = ConfigSource.fromCommandLineArgs(args.getArgs.toList)
      source = cmd <> env <> typesafe
      config <- read(implicitly[ConfigDescriptor[Config]].from(source)).orDie
      configSanitized = config.focus(_.dbTransactor.hikari.password).replace(Some("******"))
      () <- ZIO.logInfo(s"${server.BuildInfo}, configuration:\n${BlackWhite(configSanitized)}")
    } yield config
  }
}
