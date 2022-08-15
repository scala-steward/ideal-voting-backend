package cz.idealiste.idealvoting.server

import cz.idealiste.idealvoting.server
import cz.idealiste.idealvoting.server.HttpServerBlaze.*
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Server
import zio.*
import zio.config.*
import zio.config.magnolia.Descriptor
import zio.interop.catz.*

final case class HttpServerBlaze(config: Config, httpApp: HttpApp) extends HttpServer {

  lazy val server: RIO[Scope, Server] = ZIO.executorWith { executor =>
    import zio.interop.catz.implicits.*
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
    implicit lazy val configDescriptor: ConfigDescriptor[Config] =
      Descriptor.descriptor[Config]
  }

}
