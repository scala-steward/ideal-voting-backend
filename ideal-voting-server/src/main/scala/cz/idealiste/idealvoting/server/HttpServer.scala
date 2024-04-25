package cz.idealiste.idealvoting.server

import org.http4s.server.Server
import zio._

trait HttpServer {
  def server: RIO[Scope, Server]
}

object HttpServer {

  object Server {
    private[server] val layer = ZLayer.scoped(ZIO.service[HttpServer].flatMap(_.server))
  }

}
