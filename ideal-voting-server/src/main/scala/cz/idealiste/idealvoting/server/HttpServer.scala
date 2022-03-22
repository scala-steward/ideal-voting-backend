package cz.idealiste.idealvoting.server

import cz.idealiste.idealvoting.server
import org.http4s.blaze.server.BlazeServerBuilder
import zio._
import zio.config._
import zio.config.magnolia.DeriveConfigDescriptor
import zio.interop.catz._

object HttpServer {

  private[server] val layer = (
    for {
      config <- ZManaged.service[Config]
      http <- ZManaged.service[Http]
      result <- Managed.runtime.flatMap { implicit r: Runtime[Any] =>
        import zio.interop.catz.implicits._
        BlazeServerBuilder[Task]
          .withExecutionContext(r.platform.executor.asEC)
          .bindHttp(config.port, config.host)
          .withHttpApp(http.httpApp)
          .resource
          .toManagedZIO
      }
    } yield result
  ).toLayer

  final case class Config(host: String, port: Int)

  object Config {
    private[server] val layer = ZIO.service[server.Config].map(_.httpServer).toLayer
    implicit lazy val configDescriptor: ConfigDescriptor[Config] =
      DeriveConfigDescriptor.descriptor[Config]
  }

}
