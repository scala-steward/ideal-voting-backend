package cz.idealiste.idealvoting.server

import zio._
import zio.config._
import zio.config.magnolia.DeriveConfigDescriptor
import zio.config.typesafe._
import zio.doobie.liquibase._
import zio.logging.{Logger, Logging}
import zio.system.System

final case class Config(
    dbTransactor: ZIODoobieLiquibase.Config,
    httpServer: HttpServer.Config,
    voting: Voting.Config,
)

object Config {

  implicit lazy val configDescriptor: ConfigDescriptor[Config] = DeriveConfigDescriptor.descriptor[Config]

  def make(args: List[String], logger: Logger[String]): URIO[System, Config] = {
    val typesafe = TypesafeConfigSource.fromResourcePath
    val env = ConfigSource.fromSystemEnv()
    val cmd = ConfigSource.fromCommandLineArgs(args)
    val source = cmd <> env <> typesafe
    for {
      config <- read(implicitly[ConfigDescriptor[Config]].from(source))
      () <- logger.info(s"${cz.idealiste.ideal.voting.server.BuildInfo}, configuration: $config")
    } yield config
  }.orDie

  def layer(args: List[String]): URLayer[System with Logging, Has[Config]] = (
    for {
      logger <- ZIO.service[Logger[String]]
      config <- make(args, logger)
    } yield config
  ).toLayer
}
