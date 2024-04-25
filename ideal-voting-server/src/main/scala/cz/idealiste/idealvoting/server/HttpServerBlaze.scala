package cz.idealiste.idealvoting.server

import com.comcast.ip4s.{Host, Port}
import cz.idealiste.idealvoting.server
import cz.idealiste.idealvoting.server.HttpServerBlaze.Config
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import zio.Config.Error
import zio._
import zio.config.magnolia._
import zio.interop.catz._

import scala.annotation.nowarn

final case class HttpServerBlaze(config: Config, httpApp: HttpApp) extends HttpServer {

  @nowarn("msg=implicitForAsync")
  lazy val server: RIO[Scope, Server] =
    EmberServerBuilder
      .default[Task]
      .withHost(config.host)
      .withPort(config.port)
      .withHttpApp(httpApp.httpApp)
      .build
      .toScopedZIO

}

object HttpServerBlaze {

  private[server] val layer = ZLayer.fromFunction(apply _).map(_.prune[HttpServer])

  final case class Config(host: Host, port: Port)

  object Config {
    private[server] val layer = ZLayer.fromZIO(ZIO.service[server.Config].map(_.httpServer))
    private[Config] implicit lazy val hostDeriveConfig: DeriveConfig[Host] =
      DeriveConfig[String].mapOrFail(s => Host.fromString(s).toRight(Error.InvalidData(Chunk(), s)))
    private[Config] implicit lazy val portDeriveConfig: DeriveConfig[Port] =
      DeriveConfig[String].mapOrFail(s => Port.fromString(s).toRight(Error.InvalidData(Chunk(), s)))
    implicit lazy val configDescriptor: DeriveConfig[Config] =
      DeriveConfig.getDeriveConfig[Config]
  }

}
