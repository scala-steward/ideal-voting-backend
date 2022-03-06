package cz.idealiste.idealvoting.server

import cz.idealiste.idealvoting.server
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Server
import zio._
import zio.config._
import zio.config.magnolia.DeriveConfigDescriptor
import zio.interop.catz._

object HttpServer {

  def make(config: Config, http: Http): TaskManaged[Server] =
    Managed.runtime.flatMap { implicit r: Runtime[Any] =>
      import zio.interop.catz.implicits._

      BlazeServerBuilder[Task]
        .withExecutionContext(r.platform.executor.asEC)
        .bindHttp(config.port, config.host)
        .withHttpApp(http.httpApp)
        .resource
        .toManagedZIO
    }

  val layer: RLayer[Has[Config] with Has[Http], Has[Server]] = (
    for {
      config <- ZManaged.service[Config]
      http <- ZManaged.service[Http]
      result <- make(config, http)
    } yield result
  ).toLayer

  final case class Config(host: String, port: Int)
  object Config {
    val layer: RLayer[Has[server.Config], Has[Config]] = ZIO.service[server.Config].map(_.httpServer).toLayer
    implicit lazy val configDescriptor: ConfigDescriptor[Config] =
      DeriveConfigDescriptor.descriptor[Config]
  }

}
