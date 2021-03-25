package cz.idealiste.idealvoting.server

import cats.implicits.showInterpolator
import cz.idealiste.idealvoting.server.Http._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.{Router, Server}
import org.http4s.{HttpApp, HttpRoutes}
import zio.interop.catz._
import zio.random.Random
import zio.{Managed, RIO, RManaged, Runtime}

class Http(voting: Voting) {

  private val serviceV1 = {
    val Http4sDslTask: Http4sDsl[EnvTask] = Http4sDsl[EnvTask]
    import Http4sDslTask._
    import org.http4s.circe.CirceEntityCodec._

    HttpRoutes.of[EnvTask] {
      case GET -> Root / "status" =>
        Ok("OK")
      case req @ POST -> Root / "elections" =>
        for {
          req <- req.as[CreateElectionsRequest]
          adminToken <- voting.createElections(req)
          resp = CreateElectionsResponse(show"/elections/$adminToken")
          resp <- Created(resp)
        } yield resp
      case GET -> Root / "elections" / token =>
        val electionsView = voting.viewElections(token)
        Ok(electionsView)

    }
  }

  val httpApp: HttpApp[EnvTask] =
    Router("/v1" -> serviceV1).orNotFound

}

object Http {

  //  case class Email(raw: String)
  //  case class BallotOption(raw: String)
  //  case class CreateElections(name: String, author: Email, options: List[BallotOption], voters: List[Email])

  final case class CreateElectionsRequest(
      name: String,
      admin: String,
      options: List[String],
      voters: List[String],
  )

  object CreateElectionsRequest {
    implicit val decoder: Decoder[CreateElectionsRequest] = deriveDecoder[CreateElectionsRequest]
  }

  final case class CreateElectionsResponse(elections: String)

  object CreateElectionsResponse {
    implicit val encoder: Encoder[CreateElectionsResponse] = deriveEncoder[CreateElectionsResponse]
  }

  sealed abstract class ElectionsView(val name: String, val admin: String, val options: List[String])

  object ElectionsView {

    final case class Admin(
        override val name: String,
        override val admin: String,
        override val options: List[String],
        votersVoted: Map[String, Boolean],
    ) extends ElectionsView(name, admin, options)

    object Admin {
      implicit val encoder: Encoder[Admin] = deriveEncoder[Admin]
    }

    final case class Voter(
        override val name: String,
        override val admin: String,
        override val options: List[String],
    ) extends ElectionsView(name, admin, options)

    object Voter {
      implicit val encoder: Encoder[Voter] = deriveEncoder[Voter]
    }

    implicit val encoder: Encoder[ElectionsView] = deriveEncoder[ElectionsView]
  }

  type Env = Random
  type EnvTask[x] = RIO[Env, x]

  def make(config: Config.Http, voting: Voting): RManaged[Env, Server[EnvTask]] = {
    val http = new Http(voting)
    for {
      server <- Managed.runtime.flatMap { implicit r: Runtime[Env] =>
        import zio.interop.catz.implicits._
        BlazeServerBuilder[EnvTask](r.platform.executor.asEC)
          .bindHttp(config.port, config.host)
          .withHttpApp(http.httpApp)
          .resource
          .toManagedZIO
      }
    } yield server
  }

}
