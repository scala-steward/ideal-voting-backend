package cz.idealiste.idealvoting.server
import cz.idealiste.idealvoting.contract
import org.http4s
import org.http4s.server.Router
import zio.interop.catz.*
import zio.interop.catz.implicits.*
import zio.{Task, ZLayer}

final case class HttpAppLive(handler: Handler) extends HttpApp {

  lazy val httpApp: http4s.HttpApp[Task] =
    Router(
      "/v1" -> handler.oldRoutes,
      "/" -> new contract.Resource[Task]().routes(handler),
    ).orNotFound

}
object HttpAppLive {
  private[server] val layer = ZLayer.fromFunction(apply _).map(_.prune[HttpApp])
}
