package cz.idealiste.idealvoting.server

import cz.idealiste.idealvoting.server
import cz.idealiste.idealvoting.server.HttpServerBlaze.Config
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Server
import zio._
import zio.config.magnolia._
import zio.interop.catz._

final case class HttpServerBlaze(config: Config, httpApp: HttpApp) extends HttpServer {

  lazy val server: RIO[Scope, Server] = ZIO.executorWith { executor =>
    BlazeServerBuilder[Task]
      .withExecutionContext(executor.asExecutionContext)
      .bindHttp(config.port, config.host)
      .withHttpApp(httpApp.httpApp)
      .resource
      .toScopedZIO
  }

}

object HttpServerBlaze {

  private[server] val layer = ZLayer.fromFunction(apply _).map(_.prune[HttpServer])

  final case class Config(host: String, port: Int)

  object Config {
    private[server] val layer = ZLayer.fromZIO(ZIO.service[server.Config].map(_.httpServer))
    implicit lazy val configDescriptor: DeriveConfig[Config] =
      DeriveConfig.getDeriveConfig[Config]
  }

}
