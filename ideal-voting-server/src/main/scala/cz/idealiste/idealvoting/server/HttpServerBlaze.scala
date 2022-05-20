package cz.idealiste.idealvoting.server

import cz.idealiste.idealvoting.server
import cz.idealiste.idealvoting.server.HttpServerBlaze._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Server
import zio._
import zio.config._
import zio.config.magnolia.DeriveConfigDescriptor
import zio.interop.catz._

final case class HttpServerBlaze(config: Config, httpApp: HttpApp) extends HttpServer {

  lazy val server: TaskManaged[Server] = Managed.runtime.flatMap { implicit r: Runtime[Any] =>
    import zio.interop.catz.implicits._
    BlazeServerBuilder[Task]
      .withExecutionContext(r.platform.executor.asEC)
      .bindHttp(config.port, config.host)
      .withHttpApp(httpApp.httpApp)
      .resource
      .toManagedZIO
  }

}

object HttpServerBlaze {

  private[server] val layer = (apply _).toLayer[HttpServer]

  final case class Config(host: String, port: Int)

  object Config {
    private[server] val layer = ZIO.service[server.Config].map(_.httpServer).toLayer
    implicit lazy val configDescriptor: ConfigDescriptor[Config] =
      DeriveConfigDescriptor.descriptor[Config]
  }

}
