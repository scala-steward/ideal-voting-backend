package cz.idealiste.idealvoting.server

import org.http4s.server.Server
import zio.{TaskManaged, ZManaged}

trait HttpServer {
  def server: TaskManaged[Server]
}

object HttpServer {

  object Server {
    private[server] val layer = ZManaged.service[HttpServer].flatMap(_.server).toLayer
  }

}
