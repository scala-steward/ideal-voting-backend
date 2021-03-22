package cz.idealiste.idealvoting.server

import cats.data.Kleisli
import org.http4s.{HttpRoutes, Request, Response}
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server._
import zio.interop.catz._
import zio._

object Main extends App {

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    server.useForever.exitCode

  val httpApp: Kleisli[Task, Request[Task], Response[Task]] = {
    val Http4sDslTask: Http4sDsl[Task] = Http4sDsl[Task]
    import Http4sDslTask._

    val serviceV1 = HttpRoutes.of[Task] { case GET -> Root / "status" =>
      Ok("OK")
    }
    Router("/v1" -> serviceV1).orNotFound
  }

  val server: ZManaged[Any, Throwable, Server[Task]] = {

    for {
      server <- ZManaged.runtime.flatMap { implicit r: Runtime[Any] =>
        import zio.interop.catz.implicits._
        BlazeServerBuilder[Task](r.platform.executor.asEC)
          .bindHttp(8080, "localhost")
          .withHttpApp(httpApp)
          .resource
          .toManagedZIO
      }
    } yield server
  }

}
