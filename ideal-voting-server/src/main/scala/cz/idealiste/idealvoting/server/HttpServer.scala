package cz.idealiste.idealvoting.server

import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Server
import zio._
import zio.interop.catz._

object HttpServer {

  def make(config: Config.HttpServer, http: Http): TaskManaged[Server] =
    Managed.runtime.flatMap { implicit r: Runtime[Any] =>
      import zio.interop.catz.implicits._

      BlazeServerBuilder[Task](r.platform.executor.asEC)
        .bindHttp(config.port, config.host)
        .withHttpApp(http.httpApp)
        .resource
        .toManagedZIO
    }

  val layer: RLayer[Has[Config.HttpServer] with Has[Http], Has[Server]] = (
    for {
      config <- ZManaged.service[Config.HttpServer]
      http <- ZManaged.service[Http]
      result <- make(config, http)
    } yield result
  ).toLayer

}
