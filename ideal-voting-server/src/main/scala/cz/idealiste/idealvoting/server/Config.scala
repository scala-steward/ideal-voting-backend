package cz.idealiste.idealvoting.server

import zio._
import zio.config._
import zio.config.magnolia.DeriveConfigDescriptor
import zio.config.typesafe._

final case class Config(
    dbTransactor: Config.DbTransactor,
    httpServer: Config.HttpServer,
    voting: Config.Voting,
)

object Config {
  final case class DbTransactor(
      url: String,
      user: String,
      password: String,
      driverClassName: String = "org.mariadb.jdbc.Driver",
      changeLogFile: String = "db/changelog/db.changelog-master.yaml",
      threadPoolSize: Int = 32,
  )
  object DbTransactor {
    val layer: RLayer[Has[Config], Has[DbTransactor]] =
      ZLayer.fromService[Config, DbTransactor](_.dbTransactor)
    implicit lazy val configDescriptor: ConfigDescriptor[DbTransactor] =
      DeriveConfigDescriptor.descriptor[DbTransactor]
  }

  final case class HttpServer(host: String, port: Int)
  object HttpServer {
    val layer: RLayer[Has[Config], Has[HttpServer]] = ZLayer.fromService[Config, HttpServer](_.httpServer)
    implicit lazy val configDescriptor: ConfigDescriptor[HttpServer] =
      DeriveConfigDescriptor.descriptor[HttpServer]
  }

  final case class Voting(tokenLength: Int = 10)
  object Voting {
    val layer: RLayer[Has[Config], Has[Voting]] = ZLayer.fromService[Config, Voting](_.voting)
    implicit lazy val configDescriptor: ConfigDescriptor[Voting] = DeriveConfigDescriptor.descriptor[Voting]
  }

  implicit lazy val configDescriptor: ConfigDescriptor[Config] = DeriveConfigDescriptor.descriptor[Config]
  val layer: ULayer[Has[Config]] =
    TypesafeConfig.fromDefaultLoader[Config](implicitly[ConfigDescriptor[Config]]).orDie
}
