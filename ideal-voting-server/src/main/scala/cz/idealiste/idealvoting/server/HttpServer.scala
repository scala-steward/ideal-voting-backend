package cz.idealiste.idealvoting.server

import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder
import zio._
import zio.interop.catz._

object HttpServer {

  def make(config: Config.HttpServer, http: Http): TaskManaged[Server[Task]] =
    Managed.runtime.flatMap { implicit r: Runtime[Any] =>
      import zio.interop.catz.implicits._

      BlazeServerBuilder[Task](r.platform.executor.asEC)
        .bindHttp(config.port, config.host)
        .withHttpApp(http.httpApp)
        .resource
        .toManagedZIO
    }

  val layer: RLayer[Has[Config.HttpServer] with Has[Http], Has[Server[Task]]] =
    ZLayer.fromServicesManaged[Config.HttpServer, Http, Any, Throwable, Server[Task]](make)
}
