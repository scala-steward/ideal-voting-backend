package cz.idealiste.idealvoting.server

import cats.implicits.showInterpolator
import cz.idealiste.idealvoting.server.Http._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.{Router, Server}
import org.http4s.{EntityDecoder, EntityEncoder, HttpApp, HttpRoutes}
import zio.interop.catz._
import zio.random.Random
import zio.{Managed, RIO, RManaged, Runtime}

class Http(voting: Voting) {

  private val serviceV1 = {
    val Http4sDslTask: Http4sDsl[EnvTask] = Http4sDsl[EnvTask]
    import Http4sDslTask._

    HttpRoutes.of[EnvTask] {
      case GET -> Root / "status" =>
        Ok("OK")
      case req @ POST -> Root / "election" =>
        for {
          req <- req.as[CreateElectionRequest]
          adminToken <- voting.createElection(req)
          resp = CreateElectionResponse(show"v1/election/admin/$adminToken")
          resp <- Created(resp)
        } yield resp
      case GET -> Root / "election" / token =>
        for {
          electionView <- voting.viewElection(token)
          resp <- Ok(electionView)
        } yield resp
      case GET -> Root / "election" / "admin" / token =>
        for {
          electionViewAdmin <- voting.viewElectionAdmin(token)
          resp <- Ok(electionViewAdmin)
        } yield resp

    }
  }

  val httpApp: HttpApp[EnvTask] =
    Router("/v1" -> serviceV1).orNotFound

}

object Http {

  //  case class Email(raw: String)
  //  case class BallotOption(raw: String)
  //  case class CreateElections(name: String, author: Email, options: List[BallotOption], voters: List[Email])

  final case class CreateElectionRequest(
      name: String,
      admin: String,
      options: List[String],
      voters: List[String],
  )

  object CreateElectionRequest {
    implicit val decoder: Decoder[CreateElectionRequest] = deriveDecoder[CreateElectionRequest]
    implicit val encoder: Encoder[CreateElectionRequest] = deriveEncoder[CreateElectionRequest]
    implicit val entityDecoder: EntityDecoder[EnvTask, CreateElectionRequest] =
      circeEntityDecoder[EnvTask, CreateElectionRequest]
    implicit val entityEncoder: EntityEncoder[EnvTask, CreateElectionRequest] =
      circeEntityEncoder[EnvTask, CreateElectionRequest]
  }

  final case class CreateElectionResponse(election: String)

  object CreateElectionResponse {
    implicit val encoder: Encoder[CreateElectionResponse] = deriveEncoder[CreateElectionResponse]
    implicit val entityEncoder: EntityEncoder[EnvTask, CreateElectionResponse] =
      circeEntityEncoder[EnvTask, CreateElectionResponse]
    implicit val decoder: Decoder[CreateElectionResponse] = deriveDecoder[CreateElectionResponse]
    implicit val entityDecoder: EntityDecoder[EnvTask, CreateElectionResponse] =
      circeEntityDecoder[EnvTask, CreateElectionResponse]
  }

  case class ElectionView(
      name: String,
      admin: String,
      voterFullname: String,
      voter: String,
      voted: Boolean,
      options: Map[Int, String],
  )

  object ElectionView {
    implicit val encoder: Encoder[ElectionView] = deriveEncoder[ElectionView]
    implicit val entityEncoder: EntityEncoder[EnvTask, ElectionView] =
      circeEntityEncoder[EnvTask, ElectionView]
  }

  case class ElectionViewAdmin(
      name: String,
      admin: String,
      voters: Map[String, (String, Boolean)],
      options: Map[Int, String],
  )

  object ElectionViewAdmin {
    implicit val decoder: Decoder[ElectionViewAdmin] = deriveDecoder[ElectionViewAdmin]
    implicit val entityDecoder: EntityDecoder[EnvTask, ElectionViewAdmin] =
      circeEntityDecoder[EnvTask, ElectionViewAdmin]
    implicit val encoder: Encoder[ElectionViewAdmin] = deriveEncoder[ElectionViewAdmin]
    implicit val entityEncoder: EntityEncoder[EnvTask, ElectionViewAdmin] =
      circeEntityEncoder[EnvTask, ElectionViewAdmin]
  }

  type Env = Random
  type EnvTask[x] = RIO[Env, x]

  def make(config: Config.Http, voting: Voting): RManaged[Env, Server[EnvTask]] =
    Managed.runtime.flatMap { implicit r: Runtime[Env] =>
      import zio.interop.catz.implicits._

      val http = new Http(voting)

      BlazeServerBuilder[EnvTask](r.platform.executor.asEC)
        .bindHttp(config.port, config.host)
        .withHttpApp(http.httpApp)
        .resource
        .toManagedZIO
    }

}
