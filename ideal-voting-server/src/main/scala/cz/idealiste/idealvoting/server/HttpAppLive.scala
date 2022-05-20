package cz.idealiste.idealvoting.server
import cz.idealiste.idealvoting.contract
import org.http4s
import org.http4s.server.Router
import zio.Task
import zio.interop.catz._
import zio.interop.catz.implicits._

final case class HttpAppLive(handler: Handler) extends HttpApp {

  lazy val httpApp: http4s.HttpApp[Task] =
    Router(
      "/v1" -> handler.oldRoutes,
      "/" -> new contract.Resource[Task]().routes(handler),
    ).orNotFound

}
object HttpAppLive {
  private[server] val layer = (apply _).toLayer[HttpApp]
}
