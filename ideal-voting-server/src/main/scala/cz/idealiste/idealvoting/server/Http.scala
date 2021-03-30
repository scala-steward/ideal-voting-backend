package cz.idealiste.idealvoting.server

import cats.implicits.showInterpolator
import cz.idealiste.idealvoting.server.Http._
import cz.idealiste.idealvoting.server.Voting._
import emil.MailAddress
import emil.javamail.syntax._
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
          createElection = CreateElection(
            req.title,
            req.description,
            MailAddress.parseUnsafe(req.admin),
            req.options.map(req => CreateOption(req.title, req.description)),
            req.voters.map(MailAddress.parseUnsafe),
          )
          electionViewAdmin <- voting.createElection(createElection)
          resp = CreateElectionResponse(
            show"v1/election/admin/${electionViewAdmin.metadata.titleMangled}/${electionViewAdmin.admin.token}",
          )
          resp <- Created(resp)
        } yield resp
      case GET -> Root / "election" / _ / token =>
        for {
          electionView <- voting.viewElection(token)
          resp = GetElectionResponse(
            electionView.metadata.title,
            electionView.metadata.titleMangled,
            electionView.metadata.description,
            electionView.admin.email.displayString,
            electionView.options.map(o => GetOptionResponse(o.id, o.title, o.description)),
            electionView.voter.email.displayString,
            electionView.voter.token,
            electionView.voter.voted,
          )
          resp <- Ok(resp)
        } yield resp
      case GET -> Root / "election" / "admin" / _ / token =>
        for {
          electionViewAdmin <- voting.viewElectionAdmin(token)
          resp = GetElectionAdminResponse(
            electionViewAdmin.metadata.title,
            electionViewAdmin.metadata.titleMangled,
            electionViewAdmin.metadata.description,
            electionViewAdmin.admin.email.displayString,
            electionViewAdmin.admin.token,
            electionViewAdmin.options.map(o => GetOptionResponse(o.id, o.title, o.description)),
            electionViewAdmin.voters.map(v => GetVoterResponse(v.email.displayString, v.voted)),
          )
          resp <- Ok(resp)
        } yield resp

    }
  }

  val httpApp: HttpApp[EnvTask] =
    Router("/v1" -> serviceV1).orNotFound

}

object Http {

  final case class CreateOptionRequest(title: String, description: Option[String])

  object CreateOptionRequest {
    implicit val decoder: Decoder[CreateOptionRequest] = deriveDecoder[CreateOptionRequest]
    implicit val encoder: Encoder[CreateOptionRequest] = deriveEncoder[CreateOptionRequest]
    implicit val entityDecoder: EntityDecoder[EnvTask, CreateOptionRequest] =
      circeEntityDecoder[EnvTask, CreateOptionRequest]
    implicit val entityEncoder: EntityEncoder[EnvTask, CreateOptionRequest] =
      circeEntityEncoder[EnvTask, CreateOptionRequest]
  }

  final case class CreateElectionRequest(
      title: String,
      description: Option[String],
      admin: String,
      options: List[CreateOptionRequest],
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

  final case class GetOptionResponse(id: Int, title: String, description: Option[String])

  object GetOptionResponse {
    implicit val encoder: Encoder[GetOptionResponse] = deriveEncoder[GetOptionResponse]
    implicit val entityEncoder: EntityEncoder[EnvTask, GetOptionResponse] =
      circeEntityEncoder[EnvTask, GetOptionResponse]
    implicit val decoder: Decoder[GetOptionResponse] = deriveDecoder[GetOptionResponse]
    implicit val entityDecoder: EntityDecoder[EnvTask, GetOptionResponse] =
      circeEntityDecoder[EnvTask, GetOptionResponse]
  }

  final case class GetElectionResponse(
      title: String,
      titleMangled: String,
      description: Option[String],
      admin: String,
      options: List[GetOptionResponse],
      voter: String,
      voterToken: String,
      voterVoted: Boolean,
  )

  object GetElectionResponse {
    implicit val encoder: Encoder[GetElectionResponse] = deriveEncoder[GetElectionResponse]
    implicit val entityEncoder: EntityEncoder[EnvTask, GetElectionResponse] =
      circeEntityEncoder[EnvTask, GetElectionResponse]
    implicit val decoder: Decoder[GetElectionResponse] = deriveDecoder[GetElectionResponse]
    implicit val entityDecoder: EntityDecoder[EnvTask, GetElectionResponse] =
      circeEntityDecoder[EnvTask, GetElectionResponse]

  }

  final case class GetVoterResponse(voter: String, voted: Boolean)

  object GetVoterResponse {
    implicit val encoder: Encoder[GetVoterResponse] = deriveEncoder[GetVoterResponse]
    implicit val entityEncoder: EntityEncoder[EnvTask, GetVoterResponse] =
      circeEntityEncoder[EnvTask, GetVoterResponse]
    implicit val decoder: Decoder[GetVoterResponse] = deriveDecoder[GetVoterResponse]
    implicit val entityDecoder: EntityDecoder[EnvTask, GetVoterResponse] =
      circeEntityDecoder[EnvTask, GetVoterResponse]
  }

  final case class GetElectionAdminResponse(
      title: String,
      titleMangled: String,
      description: Option[String],
      admin: String,
      adminToken: String,
      options: List[GetOptionResponse],
      voters: List[GetVoterResponse],
  )

  object GetElectionAdminResponse {
    implicit val encoder: Encoder[GetElectionAdminResponse] = deriveEncoder[GetElectionAdminResponse]
    implicit val entityEncoder: EntityEncoder[EnvTask, GetElectionAdminResponse] =
      circeEntityEncoder[EnvTask, GetElectionAdminResponse]
    implicit val decoder: Decoder[GetElectionAdminResponse] = deriveDecoder[GetElectionAdminResponse]
    implicit val entityDecoder: EntityDecoder[EnvTask, GetElectionAdminResponse] =
      circeEntityDecoder[EnvTask, GetElectionAdminResponse]

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
