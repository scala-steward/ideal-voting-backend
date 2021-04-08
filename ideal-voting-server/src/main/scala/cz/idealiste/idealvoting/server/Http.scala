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
import org.http4s.server.Router
import org.http4s.{EntityDecoder, EntityEncoder, HttpApp, HttpRoutes}
import zio.interop.catz._
import zio.{Has, Task, URLayer, ZLayer}

class Http(voting: Voting) {

  private val serviceV1 = {
    val Http4sDslTask: Http4sDsl[Task] = Http4sDsl[Task]
    import Http4sDslTask._

    HttpRoutes.of[Task] {
      case GET -> Root / "status" =>
        Ok("OK")
      case req @ POST -> Root / "election" =>
        for {
          req <- req.as[CreateElectionRequest]
          createElection = CreateElection(
            req.title,
            req.description,
            req.admin,
            req.options.map(req => CreateOption(req.title, req.description)),
            req.voters,
          )
          (titleMangled, token) <- voting.createElection(createElection)
          resp = CreateElectionResponse(
            show"v1/election/admin/$titleMangled/$token",
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
            electionView.admin.email,
            electionView.options.map(o => GetOptionResponse(o.id, o.title, o.description)),
            electionView.voter.email,
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
            electionViewAdmin.admin.email,
            electionViewAdmin.admin.token,
            electionViewAdmin.options.map(o => GetOptionResponse(o.id, o.title, o.description)),
            electionViewAdmin.voters.map(v => GetVoterResponse(v.email, v.voted)),
          )
          resp <- Ok(resp)
        } yield resp

    }
  }

  val httpApp: HttpApp[Task] =
    Router("/v1" -> serviceV1).orNotFound

}

object Http {

  implicit lazy val emailDecoder: Decoder[MailAddress] = {
    final case class MailAddressStructure(name: Option[String], address: String)
    Decoder[String]
      .emap(MailAddress.parse)
      .or(deriveDecoder[MailAddressStructure].emap(s => MailAddress.parseAddressAndName(s.name, s.address)))
  }
  implicit lazy val emailEntityDecoder: EntityDecoder[Task, MailAddress] =
    circeEntityDecoder[Task, MailAddress]
  implicit lazy val emailEncoder: Encoder[MailAddress] = deriveEncoder[MailAddress]
  implicit lazy val emailEntityEncoder: EntityEncoder[Task, MailAddress] =
    circeEntityEncoder[Task, MailAddress]

  final case class CreateOptionRequest(title: String, description: Option[String])

  object CreateOptionRequest {
    implicit lazy val decoder: Decoder[CreateOptionRequest] = deriveDecoder[CreateOptionRequest]
    implicit lazy val encoder: Encoder[CreateOptionRequest] = deriveEncoder[CreateOptionRequest]
    implicit lazy val entityDecoder: EntityDecoder[Task, CreateOptionRequest] =
      circeEntityDecoder[Task, CreateOptionRequest]
    implicit lazy val entityEncoder: EntityEncoder[Task, CreateOptionRequest] =
      circeEntityEncoder[Task, CreateOptionRequest]
  }

  final case class CreateElectionRequest(
      title: String,
      description: Option[String],
      admin: MailAddress,
      options: List[CreateOptionRequest],
      voters: List[MailAddress],
  )

  object CreateElectionRequest {
    implicit lazy val decoder: Decoder[CreateElectionRequest] = deriveDecoder[CreateElectionRequest]
    implicit lazy val encoder: Encoder[CreateElectionRequest] = deriveEncoder[CreateElectionRequest]
    implicit lazy val entityDecoder: EntityDecoder[Task, CreateElectionRequest] =
      circeEntityDecoder[Task, CreateElectionRequest]
    implicit lazy val entityEncoder: EntityEncoder[Task, CreateElectionRequest] =
      circeEntityEncoder[Task, CreateElectionRequest]
  }

  final case class CreateElectionResponse(election: String)

  object CreateElectionResponse {
    implicit lazy val encoder: Encoder[CreateElectionResponse] = deriveEncoder[CreateElectionResponse]
    implicit lazy val entityEncoder: EntityEncoder[Task, CreateElectionResponse] =
      circeEntityEncoder[Task, CreateElectionResponse]
    implicit lazy val decoder: Decoder[CreateElectionResponse] = deriveDecoder[CreateElectionResponse]
    implicit lazy val entityDecoder: EntityDecoder[Task, CreateElectionResponse] =
      circeEntityDecoder[Task, CreateElectionResponse]
  }

  final case class GetOptionResponse(id: Int, title: String, description: Option[String])

  object GetOptionResponse {
    implicit lazy val encoder: Encoder[GetOptionResponse] = deriveEncoder[GetOptionResponse]
    implicit lazy val entityEncoder: EntityEncoder[Task, GetOptionResponse] =
      circeEntityEncoder[Task, GetOptionResponse]
    implicit lazy val decoder: Decoder[GetOptionResponse] = deriveDecoder[GetOptionResponse]
    implicit lazy val entityDecoder: EntityDecoder[Task, GetOptionResponse] =
      circeEntityDecoder[Task, GetOptionResponse]
  }

  final case class GetElectionResponse(
      title: String,
      titleMangled: String,
      description: Option[String],
      admin: MailAddress,
      options: List[GetOptionResponse],
      voter: MailAddress,
      voterToken: String,
      voterVoted: Boolean,
  )

  object GetElectionResponse {
    implicit lazy val encoder: Encoder[GetElectionResponse] = deriveEncoder[GetElectionResponse]
    implicit lazy val entityEncoder: EntityEncoder[Task, GetElectionResponse] =
      circeEntityEncoder[Task, GetElectionResponse]
    implicit lazy val decoder: Decoder[GetElectionResponse] = deriveDecoder[GetElectionResponse]
    implicit lazy val entityDecoder: EntityDecoder[Task, GetElectionResponse] =
      circeEntityDecoder[Task, GetElectionResponse]

  }

  final case class GetVoterResponse(voter: MailAddress, voted: Boolean)

  object GetVoterResponse {
    implicit lazy val encoder: Encoder[GetVoterResponse] = deriveEncoder[GetVoterResponse]
    implicit lazy val entityEncoder: EntityEncoder[Task, GetVoterResponse] =
      circeEntityEncoder[Task, GetVoterResponse]
    implicit lazy val decoder: Decoder[GetVoterResponse] = deriveDecoder[GetVoterResponse]
    implicit lazy val entityDecoder: EntityDecoder[Task, GetVoterResponse] =
      circeEntityDecoder[Task, GetVoterResponse]
  }

  final case class GetElectionAdminResponse(
      title: String,
      titleMangled: String,
      description: Option[String],
      admin: MailAddress,
      adminToken: String,
      options: List[GetOptionResponse],
      voters: List[GetVoterResponse],
  )

  object GetElectionAdminResponse {
    implicit lazy val encoder: Encoder[GetElectionAdminResponse] = deriveEncoder[GetElectionAdminResponse]
    implicit lazy val entityEncoder: EntityEncoder[Task, GetElectionAdminResponse] =
      circeEntityEncoder[Task, GetElectionAdminResponse]
    implicit lazy val decoder: Decoder[GetElectionAdminResponse] = deriveDecoder[GetElectionAdminResponse]
    implicit lazy val entityDecoder: EntityDecoder[Task, GetElectionAdminResponse] =
      circeEntityDecoder[Task, GetElectionAdminResponse]

  }

  def make(voting: Voting): Http = new Http(voting)

  val layer: URLayer[Has[Voting], Has[Http]] =
    ZLayer.fromService[Voting, Http](make)

}
